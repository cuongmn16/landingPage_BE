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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UnitRepository unitRepository;
    private final SubmissionRepository submissionRepository;
    private final MissionRepository missionRepository;
    private final RuleConfigService ruleConfigService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Unit createOrUpdateUnit(UnitRequest request) {
        Unit unit = unitRepository.findByCode(request.getCode())
                .orElseGet(() -> Unit.builder().code(request.getCode()).build());

        unit.setName(request.getName());
        unit.setTotalPersonnel(request.getTotalPersonnel());
        return unitRepository.save(unit);
    }

    @Transactional
    public Employee registerUserRequest(EmployeeImportRequest request) {
        String inputUnit = request.getUnitCode() != null ? request.getUnitCode().trim() : "PB_IT";

        Unit unit = unitRepository.findByCode(inputUnit)
                .or(() -> unitRepository.findByName(inputUnit))
                .orElseGet(() -> {
                    String generatedCode = "PB_" + inputUnit.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
                    if (generatedCode.length() < 4) generatedCode = "PB_GENERIC";
                    final String finalCode = generatedCode;
                    return unitRepository.findByCode(finalCode).orElseGet(() ->
                        unitRepository.save(Unit.builder().code(finalCode).name(inputUnit).totalPersonnel(10).build())
                    );
                });

        Employee employee = employeeRepository.findByEmail(request.getEmail())
                .or(() -> employeeRepository.findByEmployeeCode(request.getEmployeeCode()))
                .orElse(null);

        if (employee != null && employee.getApprovalStatus() == ApprovalStatus.ACCEPTED) {
            throw new IllegalArgumentException("Email " + request.getEmail() + " đã được Ban Tổ Chức phê duyệt (ACCEPTED) trước đó.");
        }

        if (employee == null) {
            employee = Employee.builder()
                    .employeeCode(request.getEmployeeCode())
                    .email(request.getEmail())
                    .build();
        }

        employee.setFullName(request.getFullName());
        employee.setUnit(unit);
        employee.setApprovalStatus(ApprovalStatus.PENDING);
        employee.setIsEmailVerified(request.isEmailVerified());

        if (employee.getPassword() == null || employee.getPassword().isBlank()) {
            String generatedPassword = String.valueOf((int) ((Math.random() * 900000) + 100000));
            employee.setPassword(generatedPassword);
        }

        Employee saved = employeeRepository.save(employee);

        // Notify Admin Dashboard about new PENDING request
        emailService.sendNotificationToAdmin(
                saved.getFullName(),
                saved.getEmail(),
                saved.getEmployeeCode(),
                unit.getName()
        );

        return saved;
    }

    @Transactional
    public Employee approveEmployee(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân sự với ID: " + employeeId));

        employee.setApprovalStatus(ApprovalStatus.ACCEPTED);
        employee.setIsEmailVerified(true);

        String rawPassword = employee.getPassword();
        if (rawPassword == null || rawPassword.isBlank() || rawPassword.startsWith("$2a$")) {
            rawPassword = String.valueOf((int) ((Math.random() * 900000) + 100000));
        }

        // Store BCrypt hashed password in database
        employee.setPassword(passwordEncoder.encode(rawPassword));

        Employee approved = employeeRepository.save(employee);

        // Send email with readable credentials to employee ONLY upon Admin ACCEPTANCE
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
    public Employee rejectEmployee(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân sự với ID: " + employeeId));

        employee.setApprovalStatus(ApprovalStatus.REJECTED);
        employee.setIsEmailVerified(false);
        // Save status as REJECTED, do NOT send any credentials email
        return employeeRepository.save(employee);
    }

    @Transactional
    public Employee importEmployee(EmployeeImportRequest request) {
        return registerUserRequest(request);
    }

    @Transactional(readOnly = true)
    public ValidParticipantResponse checkValidParticipant(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found with ID: " + employeeId));

        boolean isVerified = Boolean.TRUE.equals(employee.getIsEmailVerified());
        
        // Count valid submissions (status SUBMITTED or VALID)
        List<SubmissionStatus> validStatuses = Arrays.asList(SubmissionStatus.SUBMITTED, SubmissionStatus.VALID);
        long validSubmissionsCount = submissionRepository.countValidSubmissionsByEmployee(employeeId, validStatuses);

        // Get BTC Rule configuration
        SystemRuleConfig rule = ruleConfigService.getValidParticipantRule();
        long totalMissionsCount = missionRepository.count();

        long minRequired = 1;
        if (rule.getRuleType() == RuleType.MIN_COMPLETED_MISSIONS) {
            minRequired = Long.parseLong(rule.getConfigValue());
        } else if (rule.getRuleType() == RuleType.PERCENTAGE_COMPLETED_MISSIONS) {
            double ratio = Double.parseDouble(rule.getConfigValue()); // e.g. 0.25, 0.75, 1.00
            minRequired = Math.max(1, Math.round(totalMissionsCount * ratio));
        }

        boolean isValidParticipant = isVerified && (validSubmissionsCount >= minRequired);

        return ValidParticipantResponse.builder()
                .employeeId(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFullName())
                .email(employee.getEmail())
                .unitName(employee.getUnit().getName())
                .isEmailVerified(isVerified)
                .totalValidSubmissions(validSubmissionsCount)
                .totalRequiredMissions(minRequired)
                .isValidParticipant(isValidParticipant)
                .build();
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
