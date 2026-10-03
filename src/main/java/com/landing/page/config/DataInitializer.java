package com.landing.page.config;

import com.landing.page.entity.Employee;
import com.landing.page.entity.Mission;
import com.landing.page.entity.Unit;
import com.landing.page.entity.enums.Role;
import com.landing.page.repository.EmployeeRepository;
import com.landing.page.repository.MissionRepository;
import com.landing.page.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UnitRepository unitRepository;
    private final EmployeeRepository employeeRepository;
    private final MissionRepository missionRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("[DataInitializer] Initializing system default data & Admin account...");

        // Ensure approval_status column exists in PostgreSQL database table
        try {
            jdbcTemplate.execute("ALTER TABLE employees ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'PENDING';");
            log.info("✅ Database schema auto-migration: approval_status column verified.");
        } catch (Exception e) {
            log.warn("⚠️ Could not run schema migration: {}", e.getMessage());
        }

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

        // 2. Initialize Default Admin Account (All Features Access)
        if (employeeRepository.findByEmail("admin@ctin.vn").isEmpty()) {
            Employee admin = Employee.builder()
                    .employeeCode("ADMIN001")
                    .email("admin@ctin.vn")
                    .fullName("Quản Trị Viên BTC")
                    .password("admin123")
                    .role(Role.ROLE_ADMIN)
                    .approvalStatus(com.landing.page.entity.enums.ApprovalStatus.ACCEPTED)
                    .isEmailVerified(true)
                    .unit(btcUnit)
                    .build();
            employeeRepository.save(admin);
            log.info("=================================================");
            log.info("✅ CREATED DEFAULT ADMIN ACCOUNT:");
            log.info("👉 Email: admin@ctin.vn");
            log.info("👉 Password: admin123");
            log.info("👉 Role: ROLE_ADMIN (Tất cả quyền quản trị)");
            log.info("=================================================");
        }

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
}
