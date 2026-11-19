package com.landing.page.service;

import com.landing.page.dto.response.GratitudeLetterResponse;
import com.landing.page.entity.Employee;
import com.landing.page.entity.GratitudeLetter;
import com.landing.page.repository.EmployeeRepository;
import com.landing.page.repository.GratitudeLetterRepository;
import com.landing.page.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class GratitudeLetterService {

    private final GratitudeLetterRepository letterRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public GratitudeLetterResponse getMyLetter() {
        String email = SecurityUtils.currentEmail()
                .orElseThrow(() -> new IllegalArgumentException("Vui lòng đăng nhập để xem thư tri ân."));

        Employee employee = employeeRepository.findByEmail(email).orElse(null);

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

        if (letter != null) {
            return toResponse(letter);
        }

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

        return GratitudeLetterResponse.builder()
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

    @Transactional
    public void markLetterOpened() {
        String email = SecurityUtils.currentEmail().orElse(null);
        if (email == null) return;

        GratitudeLetter letter = letterRepository.findByEmailIgnoreCase(email).orElse(null);
        if (letter == null) {
            Employee emp = employeeRepository.findByEmail(email).orElse(null);
            if (emp != null && emp.getEmployeeCode() != null) {
                letter = letterRepository.findByEmployeeCodeIgnoreCase(emp.getEmployeeCode()).orElse(null);
            }
        }

        if (letter != null && !Boolean.TRUE.equals(letter.getIsOpened())) {
            letter.setIsOpened(true);
            letter.setOpenedAt(LocalDateTime.now());
            letterRepository.save(letter);
            log.info("Gratitude letter marked as opened for: {}", email);
        }
    }

    private GratitudeLetterResponse toResponse(GratitudeLetter l) {
        return GratitudeLetterResponse.builder()
                .email(l.getEmail())
                .employeeCode(l.getEmployeeCode())
                .fullName(l.getFullName())
                .salutation(l.getSalutation() != null ? l.getSalutation() : "Anh/Chị")
                .unitName(l.getUnitName())
                .daysOfService(l.getDaysOfService())
                .yearsDesc(l.getYearsDesc())
                .isOpened(Boolean.TRUE.equals(l.getIsOpened()))
                .build();
    }
}
