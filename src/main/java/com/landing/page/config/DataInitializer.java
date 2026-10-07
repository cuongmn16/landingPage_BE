package com.landing.page.config;

import com.landing.page.entity.Employee;
import com.landing.page.entity.Mission;
import com.landing.page.entity.Unit;
import com.landing.page.entity.enums.ApprovalStatus;
import com.landing.page.entity.enums.Role;
import com.landing.page.repository.EmployeeRepository;
import com.landing.page.repository.MissionRepository;
import com.landing.page.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String ADMIN_EMAIL = "admin@ctin.vn";
    private static final String ADMIN_PASSWORD = "Admin@123";

    private final UnitRepository unitRepository;
    private final EmployeeRepository employeeRepository;
    private final MissionRepository missionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        log.info("[DataInitializer] Initializing system default data & Admin account...");

        // Ensure approval_status column exists in PostgreSQL database table
        try {
            jdbcTemplate.execute("ALTER TABLE employees ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'PENDING';");
        } catch (Exception e) {
            log.warn("⚠️ Could not run schema migration: {}", e.getMessage());
        }

        hashLegacyPlainTextPasswords();

        // 1. Initialize Default Units
        Unit btcUnit = unitRepository.findByCode("PB_BTC")
                .orElseGet(() -> unitRepository.save(Unit.builder().code("PB_BTC").name("Ban Tổ Chức (BTC)").totalPersonnel(5).build()));

        unitRepository.findByCode("PB_IT")
                .orElseGet(() -> unitRepository.save(Unit.builder().code("PB_IT").name("Phòng Kỹ thuật & CNTT").totalPersonnel(50).build()));

        unitRepository.findByCode("PB_HR")
                .orElseGet(() -> unitRepository.save(Unit.builder().code("PB_HR").name("Phòng Nhân sự").totalPersonnel(10).build()));

        unitRepository.findByCode("PB_KD")
                .orElseGet(() -> unitRepository.save(Unit.builder().code("PB_KD").name("Phòng Kinh doanh").totalPersonnel(30).build()));

        unitRepository.findByCode("PB_KT")
                .orElseGet(() -> unitRepository.save(Unit.builder().code("PB_KT").name("Phòng Tài chính Kế toán").totalPersonnel(15).build()));

        // 2. Default Admin Account
        ensureAdminAccount(btcUnit);

        // 3. Initialize Default Missions (4 Missions)
        if (missionRepository.count() == 0) {
            missionRepository.saveAll(List.of(
                    Mission.builder().code("M1").title("Mission 1 – Journey Map").description("Tìm lại những dấu mốc trên hành trình phát triển CTIN.").isOpen(true).build(),
                    Mission.builder().code("M2").title("Mission 2 – Legacy Stories").description("Khám phá những câu chuyện phía sau lịch sử chính thức.").isOpen(true).build(),
                    Mission.builder().code("M3").title("Mission 3 – Leadership Legacies").description("Giải mã dấu ấn của các nhân vật tiêu biểu.").isOpen(true).build(),
                    Mission.builder().code("M4").title("Mission 4 – CTIN DNA").description("Tìm ra những giá trị và hành vi làm nên DNA CTIN.").isOpen(true).build()
            ));
            log.info("[DataInitializer] Initialized 4 default Missions.");
        }
    }

    /**
     * Older versions stored some passwords in plain text. Login no longer accepts
     * plain-text comparison, so hash them once at startup.
     */
    private void hashLegacyPlainTextPasswords() {
        List<Employee> legacy = employeeRepository.findAll().stream()
                .filter(e -> e.getPassword() != null && !e.getPassword().startsWith("$2"))
                .toList();
        for (Employee e : legacy) {
            e.setPassword(passwordEncoder.encode(e.getPassword()));
        }
        if (!legacy.isEmpty()) {
            employeeRepository.saveAll(legacy);
            log.info("🔒 [DataInitializer] Hashed {} legacy plain-text password(s) with BCrypt.", legacy.size());
        }
    }

    /**
     * Syncs the admin password on every startup and logs the credentials, so the operator
     * can always read the current admin login from the application log.
     */
    /**
     * Admin account is hard-coded: created if missing, and its password is reset to
     * ADMIN_PASSWORD on every startup.
     */
    private void ensureAdminAccount(Unit btcUnit) {
        Employee admin = employeeRepository.findByEmail(ADMIN_EMAIL).orElse(null);

        if (admin == null) {
            employeeRepository.save(Employee.builder()
                    .employeeCode("ADMIN001")
                    .email(ADMIN_EMAIL)
                    .fullName("Quản Trị Viên BTC")
                    .password(passwordEncoder.encode(ADMIN_PASSWORD))
                    .role(Role.ROLE_ADMIN)
                    .approvalStatus(ApprovalStatus.ACCEPTED)
                    .isEmailVerified(true)
                    .unit(btcUnit)
                    .build());
            log.info("✅ Created default admin account {}", ADMIN_EMAIL);
        } else if (admin.getPassword() == null || !passwordEncoder.matches(ADMIN_PASSWORD, admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            employeeRepository.save(admin);
            log.info("🔐 Admin {} password reset to the hard-coded value.", ADMIN_EMAIL);
        }

        log.warn("=================================================");
        log.warn("ADMIN LOGIN | Email: {} | Mật khẩu: {}", ADMIN_EMAIL, ADMIN_PASSWORD);
        log.warn("=================================================");
    }
}
