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
    private static final String DEMO_USER_EMAIL = "user@ctin.vn";
    private static final String DEMO_USER_PASSWORD = "User@123";

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

        // 1. Đơn vị của BTC (admin). Danh sách 12 phòng ban CTIN được seed bằng Liquibase
        //    (db/changelog/changes/004-seed-units-ctin.sql).
        Unit btcUnit = unitRepository.findByCode("PB_BTC")
                .orElseGet(() -> unitRepository.save(Unit.builder().code("PB_BTC").name("Ban Tổ Chức (BTC)").totalPersonnel(5).sortOrder(0).build()));

        // 2. Default Admin & Demo Player Accounts
        ensureAdminAccount(btcUnit);
        ensureDemoPlayerAccount(btcUnit);

        // 3. Initialize / Sync 4 Default Missions
        upsertMission("M1", "NHIỆM VỤ 01 – BẢN ĐỒ HÀNH TRÌNH", "Giải mã hành trình phát triển CTIN");
        upsertMission("M2", "NHIỆM VỤ 02 – CÂU CHUYỆN PHÍA SAU LỊCH SỬ", "Kể lại những ký ức và câu chuyện của người CTIN");
        upsertMission("M3", "NHIỆM VỤ 03 – DẤU ẤN NGƯỜI DẪN ĐƯỜNG", "Khám phá những quyết định, dấu ấn và phẩm chất lãnh đạo");
        upsertMission("M4", "NHIỆM VỤ 04 – BẢN SẮC CTIN", "Từ những giá trị được gìn giữ đến Chương 26");

        // 4. Auto-approve existing submissions for Mission 1, 3, 4 (Phương án A)
        syncAutoApprovedMissions();
    }

    private void syncAutoApprovedMissions() {
        try {
            int updated = jdbcTemplate.update(
                "UPDATE submissions SET status = 'VALID', score = 1.0 " +
                "WHERE mission_id IN (SELECT id FROM missions WHERE UPPER(code) != 'M2') " +
                "AND (status = 'SUBMITTED' OR score IS NULL)"
            );
            if (updated > 0) {
                log.info("✅ [DataInitializer] Auto-approved {} existing submission(s) for Mission 1, 3, 4.", updated);
            }
        } catch (Exception e) {
            log.warn("⚠️ [DataInitializer] Could not sync auto-approved submissions: {}", e.getMessage());
        }
    }

    private void upsertMission(String code, String title, String description) {
        Mission mission = missionRepository.findByCode(code)
                .orElseGet(() -> Mission.builder().code(code).isOpen(true).build());
        mission.setTitle(title);
        mission.setDescription(description);
        if (mission.getIsOpen() == null) {
            mission.setIsOpen(true);
        }
        missionRepository.save(mission);
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

    /**
     * Demo player account for testing gameplay without requiring an OTP email on @ctin.vn.
     */
    private void ensureDemoPlayerAccount(Unit fallbackUnit) {
        Unit playerUnit = unitRepository.findAll().stream()
                .filter(u -> !"PB_BTC".equals(u.getCode()))
                .findFirst()
                .orElse(fallbackUnit);

        Employee demoUser = employeeRepository.findByEmail(DEMO_USER_EMAIL).orElse(null);
        if (demoUser == null) {
            employeeRepository.save(Employee.builder()
                    .employeeCode("USER001")
                    .email(DEMO_USER_EMAIL)
                    .fullName("Người Chơi Thử Nghiệm")
                    .password(passwordEncoder.encode(DEMO_USER_PASSWORD))
                    .role(Role.ROLE_USER)
                    .approvalStatus(ApprovalStatus.ACCEPTED)
                    .isEmailVerified(true)
                    .unit(playerUnit)
                    .build());
            log.info("✅ Created default demo player account {}", DEMO_USER_EMAIL);
        } else {
            demoUser.setPassword(passwordEncoder.encode(DEMO_USER_PASSWORD));
            demoUser.setIsEmailVerified(true);
            demoUser.setApprovalStatus(ApprovalStatus.ACCEPTED);
            employeeRepository.save(demoUser);
        }

        log.warn("=================================================");
        log.warn("DEMO USER LOGIN | Email: {} | Mật khẩu: {}", DEMO_USER_EMAIL, DEMO_USER_PASSWORD);
        log.warn("=================================================");
    }
}
