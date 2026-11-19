package com.landing.page.service;

import com.landing.page.dto.response.GratitudeLetterResponse;
import com.landing.page.entity.Employee;
import com.landing.page.entity.GratitudeLetter;
import com.landing.page.entity.enums.Role;
import com.landing.page.repository.EmployeeRepository;
import com.landing.page.repository.GratitudeLetterRepository;
import com.landing.page.repository.SystemRuleConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GratitudeLetterServiceMappingTest {

    private GratitudeLetterRepository letterRepository;
    private EmployeeRepository employeeRepository;
    private SystemRuleConfigRepository ruleConfigRepository;
    private GratitudeLetterService service;

    private GratitudeLetter sampleLetterLan;
    private GratitudeLetter sampleLetterNang;
    private GratitudeLetter sampleLetterTai;
    private GratitudeLetter sampleLetterAnh;

    @BeforeEach
    void setUp() {
        letterRepository = mock(GratitudeLetterRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        ruleConfigRepository = mock(SystemRuleConfigRepository.class);
        service = new GratitudeLetterService(letterRepository, employeeRepository, ruleConfigRepository);

        sampleLetterLan = GratitudeLetter.builder()
                .id(1L)
                .email("lanmh@ctin.vn")
                .employeeCode("C30004")
                .fullName("Mai Hương Lan")
                .salutation("Bà")
                .unitName("Văn phòng Hồ Chí Minh")
                .daysOfService(9120)
                .yearsDesc("Hơn 24 năm 12 tháng")
                .isOpened(false)
                .isPublished(false)
                .build();

        sampleLetterNang = GratitudeLetter.builder()
                .id(2L)
                .email("nangpn@ctin.vn")
                .employeeCode("C30007")
                .fullName("Phạm Ngọc Năng")
                .salutation("Ông")
                .unitName("Văn phòng Hồ Chí Minh")
                .daysOfService(9273)
                .yearsDesc("Hơn 25 năm 4 tháng")
                .isOpened(false)
                .isPublished(false)
                .build();

        sampleLetterTai = GratitudeLetter.builder()
                .id(3L)
                .email("taihd@ctin.vn")
                .employeeCode("C30026")
                .fullName("Huỳnh Đỗ Tài")
                .salutation("Anh")
                .unitName("Trung tâm Kỹ thuật")
                .daysOfService(7022)
                .yearsDesc("Hơn 19 năm 2 tháng")
                .isOpened(false)
                .isPublished(false)
                .build();

        sampleLetterAnh = GratitudeLetter.builder()
                .id(4L)
                .email("anhlp@ctin.vn")
                .employeeCode("C30045")
                .fullName("Lê Phương Anh")
                .salutation("Chị")
                .unitName("Phòng Kế toán")
                .daysOfService(6317)
                .yearsDesc("Hơn 17 năm 3 tháng")
                .isOpened(false)
                .isPublished(false)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                email,
                "password",
                List.of(new SimpleGrantedAuthority(role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("1. Khớp chính xác email công ty: lanmh@ctin.vn -> Bà Mai Hương Lan")
    void testExactEmailMatch() {
        authenticateAs("lanmh@ctin.vn", "ROLE_USER");
        when(employeeRepository.findByEmailIgnoreCase("lanmh@ctin.vn")).thenReturn(Optional.empty());
        when(letterRepository.findByEmailIgnoreCase("lanmh@ctin.vn")).thenReturn(Optional.of(sampleLetterLan));

        GratitudeLetterResponse resp = service.getMyLetter();

        assertThat(resp).isNotNull();
        assertThat(resp.getFullName()).isEqualTo("Mai Hương Lan");
        assertThat(resp.getSalutation()).isEqualTo("Bà");
        assertThat(resp.getDaysOfService()).isEqualTo(9120);
        assertThat(resp.getEmployeeCode()).isEqualTo("C30004");
    }

    @Test
    @DisplayName("2. Khớp email không phân biệt hoa thường: LANMH@CTIN.VN -> Bà Mai Hương Lan")
    void testCaseInsensitiveEmailMatch() {
        authenticateAs("LANMH@CTIN.VN", "ROLE_USER");
        when(employeeRepository.findByEmailIgnoreCase("LANMH@CTIN.VN")).thenReturn(Optional.empty());
        when(letterRepository.findByEmailIgnoreCase("LANMH@CTIN.VN")).thenReturn(Optional.of(sampleLetterLan));

        GratitudeLetterResponse resp = service.getMyLetter();

        assertThat(resp).isNotNull();
        assertThat(resp.getFullName()).isEqualTo("Mai Hương Lan");
        assertThat(resp.getSalutation()).isEqualTo("Bà");
    }

    @Test
    @DisplayName("3. Khớp email cá nhân gmail: lanmh@gmail.com -> Tự tách prefix thành lanmh@ctin.vn")
    void testPersonalEmailDomainFallback() {
        authenticateAs("lanmh@gmail.com", "ROLE_USER");
        when(employeeRepository.findByEmailIgnoreCase("lanmh@gmail.com")).thenReturn(Optional.empty());
        when(letterRepository.findByEmailIgnoreCase("lanmh@gmail.com")).thenReturn(Optional.empty());
        when(letterRepository.findByEmailIgnoreCase("lanmh@ctin.vn")).thenReturn(Optional.of(sampleLetterLan));

        GratitudeLetterResponse resp = service.getMyLetter();

        assertThat(resp).isNotNull();
        assertThat(resp.getFullName()).isEqualTo("Mai Hương Lan");
        assertThat(resp.getSalutation()).isEqualTo("Bà");
        assertThat(resp.getDaysOfService()).isEqualTo(9120);
    }

    @Test
    @DisplayName("4. Khớp username không có đuôi domain: lanmh -> Tự ghép @ctin.vn")
    void testUsernameWithoutDomain() {
        authenticateAs("lanmh", "ROLE_USER");
        when(employeeRepository.findByEmailIgnoreCase("lanmh")).thenReturn(Optional.empty());
        when(letterRepository.findByEmailIgnoreCase("lanmh")).thenReturn(Optional.empty());
        when(letterRepository.findByEmailIgnoreCase("lanmh@ctin.vn")).thenReturn(Optional.of(sampleLetterLan));

        GratitudeLetterResponse resp = service.getMyLetter();

        assertThat(resp).isNotNull();
        assertThat(resp.getFullName()).isEqualTo("Mai Hương Lan");
        assertThat(resp.getSalutation()).isEqualTo("Bà");
    }

    @Test
    @DisplayName("5. Khớp khi đăng nhập bằng chuỗi Mã NV: C30004")
    void testLoginWithEmployeeCode() {
        authenticateAs("C30004", "ROLE_USER");
        when(employeeRepository.findByEmailIgnoreCase("C30004")).thenReturn(Optional.empty());
        when(letterRepository.findByEmailIgnoreCase("C30004")).thenReturn(Optional.empty());
        when(letterRepository.findByEmailIgnoreCase("C30004@ctin.vn")).thenReturn(Optional.empty());
        when(letterRepository.findByEmployeeCodeIgnoreCase("C30004")).thenReturn(Optional.of(sampleLetterLan));

        GratitudeLetterResponse resp = service.getMyLetter();

        assertThat(resp).isNotNull();
        assertThat(resp.getFullName()).isEqualTo("Mai Hương Lan");
        assertThat(resp.getSalutation()).isEqualTo("Bà");
    }

    @Test
    @DisplayName("6. Đăng ký email ngẫu nhiên nhưng hồ sơ có EmployeeCode: C30026 -> Anh Huỳnh Đỗ Tài")
    void testEmployeeProfileEmployeeCodeMatch() {
        authenticateAs("random_user@yahoo.com", "ROLE_USER");
        Employee emp = Employee.builder()
                .email("random_user@yahoo.com")
                .employeeCode("C30026")
                .fullName("Huỳnh Đỗ Tài")
                .role(Role.ROLE_USER)
                .build();

        when(employeeRepository.findByEmailIgnoreCase("random_user@yahoo.com")).thenReturn(Optional.of(emp));
        when(letterRepository.findByEmailIgnoreCase("random_user@yahoo.com")).thenReturn(Optional.empty());
        when(letterRepository.findByEmailIgnoreCase("random_user@ctin.vn")).thenReturn(Optional.empty());
        when(letterRepository.findByEmployeeCodeIgnoreCase("random_user@yahoo.com")).thenReturn(Optional.empty());
        when(letterRepository.findByEmployeeCodeIgnoreCase("C30026")).thenReturn(Optional.of(sampleLetterTai));

        GratitudeLetterResponse resp = service.getMyLetter();

        assertThat(resp).isNotNull();
        assertThat(resp.getFullName()).isEqualTo("Huỳnh Đỗ Tài");
        assertThat(resp.getSalutation()).isEqualTo("Anh");
        assertThat(resp.getDaysOfService()).isEqualTo(7022);
    }

    @Test
    @DisplayName("7. Đăng ký email khác, không có mã NV nhưng có Họ Tên: Lê Phương Anh -> Chị Lê Phương Anh")
    void testEmployeeProfileFullNameMatch() {
        authenticateAs("user99@domain.org", "ROLE_USER");
        Employee emp = Employee.builder()
                .email("user99@domain.org")
                .fullName("Lê Phương Anh")
                .role(Role.ROLE_USER)
                .build();

        when(employeeRepository.findByEmailIgnoreCase("user99@domain.org")).thenReturn(Optional.of(emp));
        when(letterRepository.findByEmailIgnoreCase("user99@domain.org")).thenReturn(Optional.empty());
        when(letterRepository.findByEmailIgnoreCase("user99@ctin.vn")).thenReturn(Optional.empty());
        when(letterRepository.findByEmployeeCodeIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(letterRepository.findFirstByFullNameIgnoreCase("Lê Phương Anh")).thenReturn(Optional.of(sampleLetterAnh));

        GratitudeLetterResponse resp = service.getMyLetter();

        assertThat(resp).isNotNull();
        assertThat(resp.getFullName()).isEqualTo("Lê Phương Anh");
        assertThat(resp.getSalutation()).isEqualTo("Chị");
        assertThat(resp.getDaysOfService()).isEqualTo(6317);
    }

    @Test
    @DisplayName("8. Kiểm tra đúng 4 danh xưng chuẩn: Bà, Ông, Anh, Chị")
    void testAllFourSalutations() {
        // Bà
        authenticateAs("lanmh@ctin.vn", "ROLE_USER");
        when(letterRepository.findByEmailIgnoreCase("lanmh@ctin.vn")).thenReturn(Optional.of(sampleLetterLan));
        assertThat(service.getMyLetter().getSalutation()).isEqualTo("Bà");

        // Ông
        authenticateAs("nangpn@ctin.vn", "ROLE_USER");
        when(letterRepository.findByEmailIgnoreCase("nangpn@ctin.vn")).thenReturn(Optional.of(sampleLetterNang));
        assertThat(service.getMyLetter().getSalutation()).isEqualTo("Ông");

        // Anh
        authenticateAs("taihd@ctin.vn", "ROLE_USER");
        when(letterRepository.findByEmailIgnoreCase("taihd@ctin.vn")).thenReturn(Optional.of(sampleLetterTai));
        assertThat(service.getMyLetter().getSalutation()).isEqualTo("Anh");

        // Chị
        authenticateAs("anhlp@ctin.vn", "ROLE_USER");
        when(letterRepository.findByEmailIgnoreCase("anhlp@ctin.vn")).thenReturn(Optional.of(sampleLetterAnh));
        assertThat(service.getMyLetter().getSalutation()).isEqualTo("Chị");
    }

    @Test
    @DisplayName("9. Fallback an toàn cho Admin BTC không có trong 298 thư")
    void testFallbackForAdmin() {
        authenticateAs("admin@ctin.vn", "ROLE_ADMIN");
        Employee adminEmp = Employee.builder()
                .email("admin@ctin.vn")
                .fullName("Admin Ban Tổ Chức")
                .role(Role.ROLE_ADMIN)
                .build();

        when(employeeRepository.findByEmailIgnoreCase("admin@ctin.vn")).thenReturn(Optional.of(adminEmp));
        when(letterRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(letterRepository.findByEmployeeCodeIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(letterRepository.findFirstByFullNameIgnoreCase(anyString())).thenReturn(Optional.empty());

        GratitudeLetterResponse resp = service.getMyLetter();

        assertThat(resp).isNotNull();
        assertThat(resp.getFullName()).isEqualTo("Admin Ban Tổ Chức");
        assertThat(resp.getSalutation()).isEqualTo("Ông");
        assertThat(resp.getIsPublished()).isTrue(); // Admin luôn xem được
    }
}
