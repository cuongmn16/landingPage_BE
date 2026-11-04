package com.landing.page.service;

import com.landing.page.dto.request.EmployeeImportRequest;
import com.landing.page.dto.request.UnitRequest;
import com.landing.page.dto.response.ValidParticipantResponse;
import com.landing.page.entity.Employee;
import com.landing.page.entity.SystemRuleConfig;
import com.landing.page.entity.Unit;
import com.landing.page.entity.enums.ApprovalStatus;
import com.landing.page.entity.enums.RuleType;
import com.landing.page.entity.enums.SubmissionStatus;
import com.landing.page.repository.EmployeeRepository;
import com.landing.page.repository.MissionRepository;
import com.landing.page.repository.SubmissionRepository;
import com.landing.page.repository.UnitRepository;
import com.landing.page.security.PasswordGenerator;
import com.landing.page.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    public static final List<SubmissionStatus> VALID_SUBMISSION_STATUSES =
            List.of(SubmissionStatus.SUBMITTED, SubmissionStatus.VALID);

    private final EmployeeRepository employeeRepository;
    private final UnitRepository unitRepository;
    private final SubmissionRepository submissionRepository;
    private final MissionRepository missionRepository;
    private final RuleConfigService ruleConfigService;
    private final EmailService emailService;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Unit createOrUpdateUnit(UnitRequest request) {
        Unit unit = unitRepository.findByCode(request.getCode())
                .orElseGet(() -> Unit.builder().code(request.getCode()).build());

        unit.setName(request.getName());
        unit.setTotalPersonnel(request.getTotalPersonnel());
        return unitRepository.save(unit);
    }

    /**
     * Public self-registration:
     * - Email must end with @ctin.vn
     * - User creates their own password
     * - Email must be verified by OTP beforehand
     * - Account is activated immediately (ApprovalStatus.ACCEPTED)
     */
    @Transactional
    public Employee registerUserRequest(EmployeeImportRequest request) {
        String cleanEmail = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        if (!cleanEmail.endsWith("@ctin.vn")) {
            throw new IllegalArgumentException("Email đăng ký bắt buộc phải có đuôi @ctin.vn.");
        }
        if (request.getPassword() == null || request.getPassword().trim().length() < 6) {
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất 6 ký tự.");
        }

        Unit unit = resolveUnit(request.getUnitCode());
        String cleanEmpCode = resolveEmployeeCode(request.getEmployeeCode(), cleanEmail);

        Employee employee = employeeRepository.findByEmail(cleanEmail).orElse(null);
        Employee byCode = employeeRepository.findByEmployeeCode(cleanEmpCode).orElse(null);

        if (byCode != null && (employee == null || !byCode.getId().equals(employee.getId()))) {
            throw new IllegalArgumentException("Mã nhân viên " + cleanEmpCode + " đã được đăng ký với một email khác.");
        }

        if (employee != null && employee.getApprovalStatus() == ApprovalStatus.ACCEPTED && employee.getPassword() != null) {
            throw new IllegalArgumentException("Email " + cleanEmail + " đã có tài khoản. Vui lòng dùng chức năng Đăng nhập hoặc Quên mật khẩu.");
        }

        if (!otpService.consumeVerifiedEmail(cleanEmail)) {
            throw new IllegalArgumentException("Email chưa được xác thực OTP hoặc phiên xác thực đã hết hạn. Vui lòng xác thực lại.");
        }

        if (employee == null) {
            employee = Employee.builder()
                    .employeeCode(cleanEmpCode)
                    .email(cleanEmail)
                    .build();
        }

        employee.setEmployeeCode(cleanEmpCode);
        employee.setFullName(request.getFullName().trim());
        employee.setUnit(unit);
        employee.setApprovalStatus(ApprovalStatus.ACCEPTED);
        employee.setIsEmailVerified(true);
        employee.setPassword(passwordEncoder.encode(request.getPassword().trim()));

        return employeeRepository.save(employee);
    }

    /**
     * Admin import (BTC): no OTP required.
     */
    @Transactional
    public Employee importEmployee(EmployeeImportRequest request) {
        return upsertPendingEmployee(request, !Boolean.FALSE.equals(request.getEmailVerified()));
    }

    private Employee upsertPendingEmployee(EmployeeImportRequest request, boolean emailVerified) {
        Unit unit = resolveUnit(request.getUnitCode());

        String cleanEmail = request.getEmail().trim().toLowerCase();
        String cleanEmpCode = resolveEmployeeCode(request.getEmployeeCode(), cleanEmail);

        Employee employee = employeeRepository.findByEmail(cleanEmail).orElse(null);
        Employee byCode = employeeRepository.findByEmployeeCode(cleanEmpCode).orElse(null);

        if (byCode != null && (employee == null || !byCode.getId().equals(employee.getId()))) {
            throw new IllegalArgumentException("Mã nhân viên " + cleanEmpCode + " đã được đăng ký với một email khác. Vui lòng liên hệ Ban Tổ Chức.");
        }

        if (employee != null && employee.getApprovalStatus() == ApprovalStatus.ACCEPTED) {
            throw new IllegalArgumentException("Email " + cleanEmail + " đã được kích hoạt trước đó. Vui lòng dùng chức năng Đăng nhập hoặc Quên mật khẩu.");
        }

        if (employee == null) {
            employee = Employee.builder()
                    .employeeCode(cleanEmpCode)
                    .email(cleanEmail)
                    .build();
        }

        employee.setEmployeeCode(cleanEmpCode);
        employee.setFullName(request.getFullName().trim());
        employee.setUnit(unit);
        employee.setApprovalStatus(ApprovalStatus.PENDING);
        employee.setIsEmailVerified(emailVerified);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            employee.setPassword(passwordEncoder.encode(request.getPassword().trim()));
            employee.setApprovalStatus(ApprovalStatus.ACCEPTED);
        } else {
            employee.setPassword(null);
        }

        return employeeRepository.save(employee);
    }

    private String resolveEmployeeCode(String rawCode, String cleanEmail) {
        if (rawCode != null && !rawCode.isBlank()) {
            return rawCode.trim();
        }
        String prefix = cleanEmail.split("@")[0].toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (prefix.isEmpty()) {
            prefix = "USER";
        }
        String candidate = "CTIN_" + prefix;
        Employee existing = employeeRepository.findByEmployeeCode(candidate).orElse(null);
        if (existing != null && !existing.getEmail().equalsIgnoreCase(cleanEmail)) {
            candidate = candidate + "_" + (System.currentTimeMillis() % 10000);
        }
        return candidate;
    }

    /**
     * Phòng ban người dùng chọn khi đăng ký phải là một đơn vị có sẵn (do admin quản lý);
     * không tự tạo đơn vị mới.
     */
    private Unit resolveUnit(String rawUnit) {
        if (rawUnit == null || rawUnit.isBlank()) {
            throw new IllegalArgumentException("Vui lòng chọn phòng ban.");
        }
        String inputUnit = rawUnit.trim();
        return unitRepository.findByCode(inputUnit)
                .or(() -> unitRepository.findByName(inputUnit))
                .orElseThrow(() -> new IllegalArgumentException("Phòng ban không hợp lệ: " + inputUnit));
    }

    @Transactional
    public Employee approveEmployee(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân sự với ID: " + employeeId));

        employee.setApprovalStatus(ApprovalStatus.ACCEPTED);
        employee.setIsEmailVerified(true);

        if (employee.getPassword() == null || employee.getPassword().isBlank()) {
            String rawPassword = PasswordGenerator.generate();
            employee.setPassword(passwordEncoder.encode(rawPassword));
            Employee approved = employeeRepository.save(employee);
            emailService.sendAccountCredentialsEmail(
                    approved.getEmail(),
                    approved.getFullName(),
                    approved.getEmployeeCode(),
                    rawPassword,
                    approved.getUnit().getName()
            );
            return approved;
        }

        return employeeRepository.save(employee);
    }

    @Transactional
    public Employee rejectEmployee(Long employeeId, String reason) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân sự với ID: " + employeeId));

        employee.setApprovalStatus(ApprovalStatus.REJECTED);
        employee.setIsEmailVerified(false);
        employee.setPassword(null);
        Employee rejected = employeeRepository.save(employee);

        emailService.sendRejectionEmail(rejected.getEmail(), rejected.getFullName(), reason);
        return rejected;
    }

    /**
     * Number of valid submissions required to be a "valid participant", based on BTC rule config.
     */
    @Transactional(readOnly = true)
    public long getMinRequiredMissions() {
        SystemRuleConfig rule = ruleConfigService.getValidParticipantRule();
        try {
            if (rule.getRuleType() == RuleType.MIN_COMPLETED_MISSIONS) {
                return Math.max(1, Long.parseLong(rule.getConfigValue().trim()));
            } else if (rule.getRuleType() == RuleType.PERCENTAGE_COMPLETED_MISSIONS) {
                double ratio = Double.parseDouble(rule.getConfigValue().trim()); // e.g. 0.25, 0.75, 1.00
                return Math.max(1, Math.round(missionRepository.count() * ratio));
            }
        } catch (NumberFormatException | NullPointerException e) {
            // Misconfigured rule: fall back to the default of 1
        }
        return 1;
    }

    @Transactional(readOnly = true)
    public ValidParticipantResponse checkValidParticipant(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found with ID: " + employeeId));

        boolean isVerified = Boolean.TRUE.equals(employee.getIsEmailVerified());
        long validSubmissionsCount = submissionRepository.countValidSubmissionsByEmployee(employeeId, VALID_SUBMISSION_STATUSES);
        long minRequired = getMinRequiredMissions();

        return ValidParticipantResponse.builder()
                .employeeId(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFullName())
                .email(employee.getEmail())
                .unitName(employee.getUnit().getName())
                .isEmailVerified(isVerified)
                .totalValidSubmissions(validSubmissionsCount)
                .totalRequiredMissions(minRequired)
                .isValidParticipant(isVerified && validSubmissionsCount >= minRequired)
                .build();
    }

    @Transactional(readOnly = true)
    public ValidParticipantResponse checkCurrentEmployeeValidStatus() {
        return checkValidParticipant(getCurrentEmployee().getId());
    }

    /**
     * Employee of the authenticated request (JWT). Throws if not logged in.
     */
    @Transactional(readOnly = true)
    public Employee getCurrentEmployee() {
        String email = SecurityUtils.currentEmail()
                .orElseThrow(() -> new IllegalArgumentException("Vui lòng đăng nhập lại tài khoản nhân viên."));
        return employeeRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin nhân viên. Vui lòng đăng nhập lại."));
    }

    @Transactional(readOnly = true)
    public List<Employee> getEmployeesByUnit(Long unitId) {
        return employeeRepository.findByUnitId(unitId);
    }

    @Transactional(readOnly = true)
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Unit> getAllUnits() {
        return unitRepository.findAllByOrderBySortOrderAscIdAsc();
    }
}
