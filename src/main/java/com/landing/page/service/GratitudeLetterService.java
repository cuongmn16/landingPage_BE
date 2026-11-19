package com.landing.page.service;

import com.landing.page.dto.response.GratitudeLetterAdminSummaryResponse;
import com.landing.page.dto.response.GratitudeLetterResponse;
import com.landing.page.entity.Employee;
import com.landing.page.entity.GratitudeLetter;
import com.landing.page.entity.SystemRuleConfig;
import com.landing.page.entity.enums.RuleType;
import com.landing.page.repository.EmployeeRepository;
import com.landing.page.repository.GratitudeLetterRepository;
import com.landing.page.repository.SystemRuleConfigRepository;
import com.landing.page.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GratitudeLetterService {

    public static final String KEY_GRATITUDE_LETTERS_PUBLISHED = "GRATITUDE_LETTERS_PUBLISHED";
    public static final String KEY_GRATITUDE_LETTERS_MODE = "GRATITUDE_LETTERS_MODE"; // AUTO_20H | MANUAL_PUBLISHED | MANUAL_LOCKED

    private final GratitudeLetterRepository letterRepository;
    private final EmployeeRepository employeeRepository;
    private final SystemRuleConfigRepository ruleConfigRepository;

    @Transactional(readOnly = true)
    public String getPublishMode() {
        return ruleConfigRepository.findByConfigKey(KEY_GRATITUDE_LETTERS_MODE)
                .map(SystemRuleConfig::getConfigValue)
                .orElse("AUTO_20H"); // Mặc định: Tự động lúc 20:00
    }

    @Transactional
    public String setPublishMode(String mode) {
        String cleanMode = (mode != null && !mode.isBlank()) ? mode.trim().toUpperCase() : "AUTO_20H";
        SystemRuleConfig config = ruleConfigRepository.findByConfigKey(KEY_GRATITUDE_LETTERS_MODE)
                .orElseGet(() -> SystemRuleConfig.builder()
                        .configKey(KEY_GRATITUDE_LETTERS_MODE)
                        .ruleType(RuleType.MIN_COMPLETED_MISSIONS)
                        .description("Che do phat hanh Thu Tri An (AUTO_20H | MANUAL_PUBLISHED | MANUAL_LOCKED)")
                        .build());

        config.setConfigValue(cleanMode);
        ruleConfigRepository.save(config);
        log.info("Admin updated Gratitude Letters publish mode to: {}", cleanMode);
        return cleanMode;
    }

    @Transactional(readOnly = true)
    public boolean isPublished() {
        String mode = getPublishMode();
        if ("MANUAL_PUBLISHED".equalsIgnoreCase(mode)) {
            return true;
        }
        if ("MANUAL_LOCKED".equalsIgnoreCase(mode)) {
            return false;
        }
        // Chế độ AUTO_20H: Tự động phát hành từ 20:00 ngày 20/11/2026 trở đi (Múi giờ Asia/Ho_Chi_Minh)
        try {
            java.time.ZoneId vnZone = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
            java.time.LocalDateTime now = java.time.LocalDateTime.now(vnZone);
            java.time.LocalDateTime releaseTarget = java.time.LocalDateTime.of(2026, 11, 20, 20, 0, 0);
            return !now.isBefore(releaseTarget);
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public boolean setPublished(boolean published) {
        setPublishMode(published ? "MANUAL_PUBLISHED" : "MANUAL_LOCKED");
        return isPublished();
    }

    @Transactional(readOnly = true)
    public GratitudeLetterAdminSummaryResponse getAdminSummary() {
        String mode = getPublishMode();
        boolean published = isPublished();
        List<GratitudeLetter> list = letterRepository.findAllByOrderByIdAsc();
        long openedCount = list.stream().filter(l -> Boolean.TRUE.equals(l.getIsOpened())).count();

        List<GratitudeLetterResponse> responses = list.stream()
                .map(this::toResponse)
                .toList();

        return GratitudeLetterAdminSummaryResponse.builder()
                .isPublished(published)
                .publishMode(mode)
                .total((long) list.size())
                .openedCount(openedCount)
                .letters(responses)
                .build();
    }

    @Transactional(readOnly = true)
    public GratitudeLetterResponse getLetterForAdminPreview(Long id) {
        GratitudeLetter letter = letterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thư tri ân với ID: " + id));
        return toResponse(letter);
    }

    @Transactional
    public GratitudeLetterResponse setIndividualPublished(Long id, boolean published) {
        GratitudeLetter letter = letterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thư tri ân với ID: " + id));
        letter.setIsPublished(published);
        letterRepository.save(letter);
        log.info("Admin updated individual publish status for letter ID {} ({}): {}", id, letter.getFullName(), published);
        return toResponse(letter);
    }

    @Transactional(readOnly = true)
    public GratitudeLetterResponse getMyLetter() {
        String email = SecurityUtils.currentEmail()
                .orElseThrow(() -> new IllegalArgumentException("Vui lòng đăng nhập để xem thư tri ân."));

        String cleanEmail = (email != null) ? email.trim() : "";
        Employee employee = employeeRepository.findByEmailIgnoreCase(cleanEmail)
                .orElseGet(() -> employeeRepository.findByEmail(cleanEmail).orElse(null));

        boolean isAdmin = SecurityUtils.isAdmin() ||
                (employee != null && employee.getRole() != null &&
                        (employee.getRole() == com.landing.page.entity.enums.Role.ROLE_ADMIN ||
                                "ROLE_ADMIN".equalsIgnoreCase(employee.getRole().name()) ||
                                "ADMIN".equalsIgnoreCase(employee.getRole().name())));
        boolean published = isPublished();

        GratitudeLetter letter = findMatchingLetter(cleanEmail, employee);

        GratitudeLetterResponse resp;
        if (letter != null) {
            resp = toResponse(letter);
        } else {
            // Fallback an toàn cho tài khoản test / admin chưa có trong danh sách
            boolean isBtcAdmin = isAdmin || cleanEmail.toLowerCase().contains("admin");
            String fullName = (employee != null && employee.getFullName() != null)
                    ? employee.getFullName()
                    : (isBtcAdmin ? "Quản Trị Viên BTC" : "Trần Tuấn Anh");
            String unitName = isBtcAdmin ? "Ban Tổ Chức (BTC)" : "CTIN";
            if (employee != null) {
                try {
                    if (employee.getUnit() != null) {
                        unitName = employee.getUnit().getName();
                    }
                } catch (Exception ignored) {
                }
            }

            resp = GratitudeLetterResponse.builder()
                    .email(cleanEmail)
                    .employeeCode(employee != null ? employee.getEmployeeCode() : (isBtcAdmin ? "ADMIN001" : "C30050"))
                    .fullName(fullName)
                    .salutation("Anh")
                    .unitName(unitName)
                    .daysOfService(isBtcAdmin ? 9130 : 4859)
                    .yearsDesc(isBtcAdmin
                            ? "Hơn 25 năm đầy nhiệt huyết & gắn bó cùng đại gia đình CTIN"
                            : "Hơn 13 năm 3 tháng đầy nhiệt huyết & gắn bó cùng đại gia đình CTIN")
                    .isOpened(false)
                    .build();
        }

        boolean isIndividualPublished = (letter != null && Boolean.TRUE.equals(letter.getIsPublished()));
        resp.setIsPublished(published || isAdmin || isIndividualPublished);
        return resp;
    }

    @Transactional
    public void markLetterOpened() {
        String email = SecurityUtils.currentEmail().orElse(null);
        if (email == null) return;

        String cleanEmail = email.trim();
        Employee employee = employeeRepository.findByEmailIgnoreCase(cleanEmail)
                .orElseGet(() -> employeeRepository.findByEmail(cleanEmail).orElse(null));

        GratitudeLetter letter = findMatchingLetter(cleanEmail, employee);

        if (letter != null && !Boolean.TRUE.equals(letter.getIsOpened())) {
            letter.setIsOpened(true);
            letter.setOpenedAt(LocalDateTime.now());
            letterRepository.save(letter);
            log.info("Đã đánh dấu đã mở thư tri ân cho nhân sự: {} ({})", letter.getFullName(), letter.getEmail());
        }
    }

    /**
     * Cơ chế mapping đa tầng kết nối chính xác thư tri ân theo tài khoản đăng ký / đăng nhập:
     * 1. Email chính xác (case-insensitive).
     * 2. Tiền tố email ghép đuôi @ctin.vn (ví dụ lanmh@gmail.com -> lanmh@ctin.vn).
     * 3. Chuỗi email người dùng nhập là Mã NV (ví dụ C30004).
     * 4. Email lưu trong hồ sơ Employee.
     * 5. Mã nhân viên (employeeCode) lưu trong hồ sơ Employee.
     * 6. Họ và tên (fullName) của Employee.
     */
    private GratitudeLetter findMatchingLetter(String cleanEmail, Employee employee) {
        if ((cleanEmail == null || cleanEmail.isBlank()) && employee == null) {
            return null;
        }

        GratitudeLetter letter = null;

        // Tầng 1: Khớp chính xác theo Email đăng nhập
        if (cleanEmail != null && !cleanEmail.isBlank()) {
            letter = letterRepository.findByEmailIgnoreCase(cleanEmail).orElse(null);
            if (letter != null) return letter;

            // Tầng 2: Nếu email có @, lấy prefix ghép với @ctin.vn (vd: lanmh@gmail.com -> lanmh@ctin.vn)
            if (cleanEmail.contains("@")) {
                String prefix = cleanEmail.substring(0, cleanEmail.indexOf("@")).trim();
                if (!prefix.isBlank()) {
                    letter = letterRepository.findByEmailIgnoreCase(prefix + "@ctin.vn").orElse(null);
                    if (letter != null) return letter;
                }
            } else {
                // Tầng 2b: Người dùng chỉ nhập username prefix không có @ (vd: lanmh)
                letter = letterRepository.findByEmailIgnoreCase(cleanEmail + "@ctin.vn").orElse(null);
                if (letter != null) return letter;
            }

            // Tầng 3: Kiểm tra xem chuỗi đăng nhập có phải là Mã nhân viên không (vd: C30004)
            letter = letterRepository.findByEmployeeCodeIgnoreCase(cleanEmail).orElse(null);
            if (letter != null) return letter;
        }

        // Tầng 4: Kiểm tra email trong hồ sơ Employee (nếu khác email đăng nhập)
        if (employee != null && employee.getEmail() != null) {
            String empEmail = employee.getEmail().trim();
            if (!empEmail.equalsIgnoreCase(cleanEmail)) {
                letter = letterRepository.findByEmailIgnoreCase(empEmail).orElse(null);
                if (letter != null) return letter;
                if (empEmail.contains("@")) {
                    String prefix = empEmail.substring(0, empEmail.indexOf("@")).trim();
                    letter = letterRepository.findByEmailIgnoreCase(prefix + "@ctin.vn").orElse(null);
                    if (letter != null) return letter;
                }
            }
        }

        // Tầng 5: Khớp theo Mã NV (employeeCode) của nhân sự
        if (employee != null && employee.getEmployeeCode() != null && !employee.getEmployeeCode().isBlank()) {
            letter = letterRepository.findByEmployeeCodeIgnoreCase(employee.getEmployeeCode().trim()).orElse(null);
            if (letter != null) return letter;
        }

        // Tầng 6: Khớp theo Họ và tên của nhân sự
        if (employee != null && employee.getFullName() != null && !employee.getFullName().isBlank()) {
            letter = letterRepository.findFirstByFullNameIgnoreCase(employee.getFullName().trim()).orElse(null);
            if (letter != null) return letter;
        }

        return null;
    }

    private GratitudeLetterResponse toResponse(GratitudeLetter letter) {
        boolean systemPublished = isPublished();
        boolean individualPublished = Boolean.TRUE.equals(letter.getIsPublished());

        // Quy đổi danh xưng thân thiện, trẻ trung: Ông -> Anh, Bà -> Chị
        String rawSalutation = letter.getSalutation();
        String salutation = "Anh";
        if (rawSalutation != null) {
            String clean = rawSalutation.trim();
            if ("Bà".equalsIgnoreCase(clean) || "Chị".equalsIgnoreCase(clean)) {
                salutation = "Chị";
            } else {
                salutation = "Anh";
            }
        }

        return GratitudeLetterResponse.builder()
                .id(letter.getId())
                .email(letter.getEmail())
                .employeeCode(letter.getEmployeeCode())
                .fullName(letter.getFullName())
                .salutation(salutation)
                .unitName(letter.getUnitName())
                .daysOfService(letter.getDaysOfService())
                .yearsDesc(letter.getYearsDesc())
                .isOpened(letter.getIsOpened())
                .openedAt(letter.getOpenedAt())
                .isPublished(systemPublished || individualPublished)
                .build();
    }
}
