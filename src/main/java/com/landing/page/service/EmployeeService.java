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
     * Public self-registration: the email must have been verified by OTP beforehand.
     */
    @Transactional
    public Employee registerUserRequest(EmployeeImportRequest request) {
        // Validate & save first so that a business error does not burn the OTP verification;
        // if the email is not verified the exception rolls the transaction back.
        Employee saved = upsertPendingEmployee(request, true);
        if (!otpService.consumeVerifiedEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email chưa được xác thực OTP hoặc phiên xác thực đã hết hạn. Vui lòng xác thực lại.");
        }

        emailService.sendNotificationToAdmin(
                saved.getFullName(),
                saved.getEmail(),
                saved.getEmployeeCode(),
                saved.getUnit().getName()
        );
        return saved;
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
        String cleanEmpCode = request.getEmployeeCode().trim();

        Employee employee = employeeRepository.findByEmail(cleanEmail).orElse(null);
        Employee byCode = employeeRepository.findByEmployeeCode(cleanEmpCode).orElse(null);

        if (byCode != null && (employee == null || !byCode.getId().equals(employee.getId()))) {
            throw new IllegalArgumentException("Mã nhân viên " + cleanEmpCode + " đã được đăng ký với một email khác. Vui lòng liên hệ Ban Tổ Chức.");
        }

        if (employee != null && employee.getApprovalStatus() == ApprovalStatus.ACCEPTED) {
            throw new IllegalArgumentException("Email " + cleanEmail + " đã được Ban Tổ Chức phê duyệt (ACCEPTED) trước đó. Vui lòng dùng chức năng Đăng nhập hoặc Quên mật khẩu.");
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
        employee.setPassword(null); // No password is created until Admin accepts

        return employeeRepository.save(employee);
    }

    private Unit resolveUnit(String rawUnit) {
        String inputUnit = rawUnit != null && !rawUnit.isBlank() ? rawUnit.trim() : "PB_IT";
        return unitRepository.findByCode(inputUnit)
                .or(() -> unitRepository.findByName(inputUnit))
                .orElseGet(() -> {
                    String generatedCode = "PB_" + inputUnit.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
                    if (generatedCode.length() < 4) generatedCode = "PB_GENERIC";
                    final String finalCode = generatedCode;
                    return unitRepository.findByCode(finalCode).orElseGet(() ->
                            unitRepository.save(Unit.builder().code(finalCode).name(inputUnit).totalPersonnel(10).build())
                    );
                });
    }

    @Transactional
    public Employee approveEmployee(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân sự với ID: " + employeeId));

        employee.setApprovalStatus(ApprovalStatus.ACCEPTED);
        employee.setIsEmailVerified(true);

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
        return unitRepository.findAll();
    }
}
