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

        Employee employee = employeeRepository.findByEmail(email).orElse(null);
        boolean isAdmin = employee != null && employee.getRole() != null && "ADMIN".equalsIgnoreCase(employee.getRole().name());
        boolean published = isPublished();

        String cleanEmail = (email != null) ? email.trim() : "";

        // 1. Khớp ưu tiên số 1: Khớp chính xác theo Email
        GratitudeLetter letter = letterRepository.findByEmailIgnoreCase(cleanEmail).orElse(null);

        // 2. Khớp ưu tiên số 2: Nếu email đăng ký khác domain (VD: lanmh@gmail.com), thử khớp theo username @ctin.vn (lanmh@ctin.vn)
        if (letter == null && cleanEmail.contains("@")) {
            String prefix = cleanEmail.substring(0, cleanEmail.indexOf("@")).trim();
            letter = letterRepository.findByEmailIgnoreCase(prefix + "@ctin.vn").orElse(null);
        }

        // 3. Khớp ưu tiên số 3: Nếu chưa thấy theo email, thử khớp theo Mã NV của nhân sự
        if (letter == null && employee != null && employee.getEmployeeCode() != null && !employee.getEmployeeCode().isBlank()) {
            letter = letterRepository.findByEmployeeCodeIgnoreCase(employee.getEmployeeCode().trim()).orElse(null);
        }

        // 4. Khớp ưu tiên số 4: Nếu vẫn chưa thấy, thử khớp theo Họ và tên
        if (letter == null && employee != null && employee.getFullName() != null && !employee.getFullName().isBlank()) {
            letter = letterRepository.findFirstByFullNameIgnoreCase(employee.getFullName().trim()).orElse(null);
        }

        GratitudeLetterResponse resp;
        if (letter != null) {
            resp = toResponse(letter);
        } else {
            // Fallback an toàn cho tài khoản test / admin chưa có trong danh sách
            boolean isBtcAdmin = isAdmin || (email != null && email.toLowerCase().contains("admin"));
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
                    .email(email)
                    .employeeCode(employee != null ? employee.getEmployeeCode() : (isBtcAdmin ? "ADMIN001" : "C30050"))
                    .fullName(fullName)
                    .salutation(isBtcAdmin ? "Ông" : "Anh")
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
        GratitudeLetter letter = letterRepository.findByEmailIgnoreCase(cleanEmail).orElse(null);
        if (letter == null && cleanEmail.contains("@")) {
            String prefix = cleanEmail.substring(0, cleanEmail.indexOf("@")).trim();
            letter = letterRepository.findByEmailIgnoreCase(prefix + "@ctin.vn").orElse(null);
        }
        if (letter == null) {
            Employee employee = employeeRepository.findByEmail(cleanEmail).orElse(null);
            if (employee != null && employee.getEmployeeCode() != null && !employee.getEmployeeCode().isBlank()) {
                letter = letterRepository.findByEmployeeCodeIgnoreCase(employee.getEmployeeCode().trim()).orElse(null);
            }
            if (letter == null && employee != null && employee.getFullName() != null && !employee.getFullName().isBlank()) {
                letter = letterRepository.findFirstByFullNameIgnoreCase(employee.getFullName().trim()).orElse(null);
            }
        }

        if (letter != null && !Boolean.TRUE.equals(letter.getIsOpened())) {
            letter.setIsOpened(true);
            letter.setOpenedAt(LocalDateTime.now());
            letterRepository.save(letter);
            log.info("Đã đánh dấu đã mở thư tri ân cho nhân sự: {} ({})", letter.getFullName(), letter.getEmail());
        }
    }

    private GratitudeLetterResponse toResponse(GratitudeLetter letter) {
        boolean systemPublished = isPublished();
        boolean individualPublished = Boolean.TRUE.equals(letter.getIsPublished());
        return GratitudeLetterResponse.builder()
                .id(letter.getId())
                .email(letter.getEmail())
                .employeeCode(letter.getEmployeeCode())
                .fullName(letter.getFullName())
                .salutation(letter.getSalutation() != null ? letter.getSalutation() : "Anh/Chị")
                .unitName(letter.getUnitName())
                .daysOfService(letter.getDaysOfService())
                .yearsDesc(letter.getYearsDesc())
                .isOpened(letter.getIsOpened())
                .openedAt(letter.getOpenedAt())
                .isPublished(systemPublished || individualPublished)
                .build();
    }
}
