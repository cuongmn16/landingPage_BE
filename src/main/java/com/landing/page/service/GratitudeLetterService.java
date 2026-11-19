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

    private final GratitudeLetterRepository letterRepository;
    private final EmployeeRepository employeeRepository;
    private final SystemRuleConfigRepository ruleConfigRepository;

    @Transactional(readOnly = true)
    public boolean isPublished() {
        return ruleConfigRepository.findByConfigKey(KEY_GRATITUDE_LETTERS_PUBLISHED)
                .map(c -> Boolean.parseBoolean(c.getConfigValue()))
                .orElse(false);
    }

    @Transactional
    public boolean setPublished(boolean published) {
        SystemRuleConfig config = ruleConfigRepository.findByConfigKey(KEY_GRATITUDE_LETTERS_PUBLISHED)
                .orElseGet(() -> SystemRuleConfig.builder()
                        .configKey(KEY_GRATITUDE_LETTERS_PUBLISHED)
                        .ruleType(RuleType.MIN_COMPLETED_MISSIONS)
                        .description("Trang thai phat hanh Thu Tri An CTIN 25 Nam (true/false)")
                        .build());

        config.setConfigValue(String.valueOf(published));
        ruleConfigRepository.save(config);
        log.info("Admin updated Gratitude Letters published status to: {}", published);
        return published;
    }

    @Transactional(readOnly = true)
    public GratitudeLetterAdminSummaryResponse getAdminSummary() {
        boolean published = isPublished();
        List<GratitudeLetter> list = letterRepository.findAllByOrderByIdAsc();
        long openedCount = list.stream().filter(l -> Boolean.TRUE.equals(l.getIsOpened())).count();

        List<GratitudeLetterResponse> responses = list.stream()
                .map(this::toResponse)
                .toList();

        return GratitudeLetterAdminSummaryResponse.builder()
                .isPublished(published)
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

    @Transactional(readOnly = true)
    public GratitudeLetterResponse getMyLetter() {
        String email = SecurityUtils.currentEmail()
                .orElseThrow(() -> new IllegalArgumentException("Vui lòng đăng nhập để xem thư tri ân."));

        Employee employee = employeeRepository.findByEmail(email).orElse(null);
        boolean isAdmin = employee != null && employee.getRole() != null && "ADMIN".equalsIgnoreCase(employee.getRole().name());
        boolean published = isPublished();

        // 1. Khớp ưu tiên số 1: Khớp chính xác theo Email
        GratitudeLetter letter = letterRepository.findByEmailIgnoreCase(email).orElse(null);

        // 2. Khớp ưu tiên số 2: Nếu chưa thấy theo email, thử khớp theo Mã NV của nhân sự
        if (letter == null && employee != null && employee.getEmployeeCode() != null) {
            letter = letterRepository.findByEmployeeCodeIgnoreCase(employee.getEmployeeCode()).orElse(null);
        }

        // 3. Khớp ưu tiên số 3: Nếu vẫn chưa thấy, thử khớp theo Họ và tên
        if (letter == null && employee != null && employee.getFullName() != null) {
            letter = letterRepository.findFirstByFullNameIgnoreCase(employee.getFullName().trim()).orElse(null);
        }

        GratitudeLetterResponse resp;
        if (letter != null) {
            resp = toResponse(letter);
        } else {
            // Fallback an toàn tuyệt đối cho tài khoản test / admin chưa có trong danh sách
            String fullName = (employee != null && employee.getFullName() != null) ? employee.getFullName() : "Cán bộ nhân viên CTIN";
            String unitName = "CTIN";
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
                    .employeeCode(employee != null ? employee.getEmployeeCode() : "CTIN_A25")
                    .fullName(fullName)
                    .salutation("Anh/Chị")
                    .unitName(unitName)
                    .daysOfService(3485)
                    .yearsDesc("Hơn 9 năm 6 tháng đầy nhiệt huyết & gắn bó cùng đại gia đình CTIN")
                    .isOpened(false)
                    .build();
        }

        resp.setIsPublished(published || isAdmin);
        return resp;
    }

    @Transactional
    public void markLetterOpened() {
        String email = SecurityUtils.currentEmail().orElse(null);
        if (email == null) return;

        GratitudeLetter letter = letterRepository.findByEmailIgnoreCase(email).orElse(null);
        if (letter == null) {
            Employee employee = employeeRepository.findByEmail(email).orElse(null);
            if (employee != null && employee.getEmployeeCode() != null) {
                letter = letterRepository.findByEmployeeCodeIgnoreCase(employee.getEmployeeCode()).orElse(null);
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
                .isPublished(isPublished())
                .build();
    }
}
