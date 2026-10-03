package com.landing.page.controller;

import com.landing.page.dto.request.LoginRequest;
import com.landing.page.dto.request.RefreshTokenRequest;
import com.landing.page.dto.response.ApiResponse;
import com.landing.page.dto.response.AuthResponse;
import com.landing.page.entity.Employee;
import com.landing.page.repository.EmployeeRepository;
import com.landing.page.security.JwtTokenProvider;
import com.landing.page.service.EmailService;
import com.landing.page.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Random;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OtpService otpService;
    private final EmailService emailService;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<String>> sendOtp(@RequestParam("email") String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Địa chỉ email không được để trống.");
        }
        otpService.generateAndSendOtp(email);
        return ResponseEntity.ok(ApiResponse.ok("Mã OTP 6 số đã được gửi tới email " + email + ". Vui lòng kiểm tra hộp thư!", email));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Boolean>> verifyOtp(@RequestParam("email") String email, @RequestParam("otpCode") String otpCode) {
        otpService.verifyOtp(email, otpCode);
        return ResponseEntity.ok(ApiResponse.ok("Xác thực OTP thành công! Email đã được xác minh tồn tại thực tế.", true));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        String input = request.getUsernameOrEmail().trim();
        Employee employee = employeeRepository.findByEmail(input)
                .or(() -> employeeRepository.findByEmployeeCode(input))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản với email/mã nhân viên: " + input));

        String inputPassword = request.getPassword();
        String dbPassword = employee.getPassword();

        // 1. Verify password using BCrypt with legacy plain text fallback support
        boolean isMatch = (dbPassword != null) &&
                (passwordEncoder.matches(inputPassword, dbPassword) || inputPassword.equals(dbPassword));

        if (!isMatch) {
            throw new IllegalArgumentException("Mật khẩu không chính xác.");
        }

        // Auto-upgrade legacy plain text password to BCrypt hash in DB if needed
        if (inputPassword.equals(dbPassword) && (dbPassword == null || !dbPassword.startsWith("$2a$"))) {
            employee.setPassword(passwordEncoder.encode(inputPassword));
            employeeRepository.save(employee);
            log.info("🔒 [BCrypt] Auto-upgraded password to BCrypt hash for user {}", employee.getEmail());
        }

        // 2. Generate Access Token & Refresh Token
        String accessToken = jwtTokenProvider.generateAccessToken(employee);
        String refreshToken = jwtTokenProvider.generateRefreshToken(employee);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .token(accessToken) // Backward-compatibility alias for frontend
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationMs())
                .employeeId(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .email(employee.getEmail())
                .fullName(employee.getFullName())
                .role(employee.getRole())
                .unitCode(employee.getUnit().getCode())
                .unitName(employee.getUnit().getName())
                .build();

        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công với quyền " + employee.getRole(), authResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Refresh Token không hợp lệ hoặc đã hết hạn.");
        }

        String email = jwtTokenProvider.getEmailFromToken(refreshToken);
        if (!jwtTokenProvider.validateRefreshToken(email, refreshToken)) {
            throw new IllegalArgumentException("Refresh Token đã bị thu hồi hoặc không chính xác.");
        }

        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin tài khoản nhân viên."));

        String newAccessToken = jwtTokenProvider.generateAccessToken(employee);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(employee);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .token(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationMs())
                .employeeId(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .email(employee.getEmail())
                .fullName(employee.getFullName())
                .role(employee.getRole())
                .unitCode(employee.getUnit().getCode())
                .unitName(employee.getUnit().getName())
                .build();

        return ResponseEntity.ok(ApiResponse.ok("Cấp lại AccessToken thành công!", authResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(@RequestParam("email") String email) {
        jwtTokenProvider.revokeRefreshToken(email);
        return ResponseEntity.ok(ApiResponse.ok("Đăng xuất và thu hồi Refresh Token thành công.", email));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@RequestParam("email") String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập địa chỉ email của bạn.");
        }
        String cleanEmail = email.trim().toLowerCase();
        Employee employee = employeeRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản nhân viên tương ứng với email: " + cleanEmail));

        // Generate a new 6-digit random password for security
        String rawPassword = String.format("%06d", new Random().nextInt(900000) + 100000);
        employee.setPassword(passwordEncoder.encode(rawPassword));
        employeeRepository.save(employee);

        emailService.sendForgotPasswordEmail(
                employee.getEmail(),
                employee.getFullName(),
                employee.getEmployeeCode(),
                rawPassword
        );

        return ResponseEntity.ok(ApiResponse.ok("Mật khẩu mới đã được khởi tạo và gửi tới email " + cleanEmail + ". Vui lòng kiểm tra hộp thư!", cleanEmail));
    }
}
