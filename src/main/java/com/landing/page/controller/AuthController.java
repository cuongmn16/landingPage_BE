package com.landing.page.controller;

import com.landing.page.dto.request.LoginRequest;
import com.landing.page.dto.request.RefreshTokenRequest;
import com.landing.page.dto.response.ApiResponse;
import com.landing.page.dto.response.AuthResponse;
import com.landing.page.entity.Employee;
import com.landing.page.entity.enums.ApprovalStatus;
import com.landing.page.repository.EmployeeRepository;
import com.landing.page.security.JwtTokenProvider;
import com.landing.page.security.PasswordGenerator;
import com.landing.page.security.SecurityUtils;
import com.landing.page.service.EmailService;
import com.landing.page.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BAD_CREDENTIALS = "Email/mã nhân viên hoặc mật khẩu không chính xác.";

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
        return ResponseEntity.ok(ApiResponse.ok("Xác thực OTP thành công!", true));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        String input = request.getUsernameOrEmail().trim();
        Employee employee = employeeRepository.findByEmail(input.toLowerCase())
                .or(() -> employeeRepository.findByEmail(input))
                .or(() -> employeeRepository.findByEmployeeCode(input))
                .orElseThrow(() -> new IllegalArgumentException(BAD_CREDENTIALS));

        if (employee.getApprovalStatus() != ApprovalStatus.ACCEPTED) {
            throw new IllegalArgumentException("Tài khoản của bạn đang ở trạng thái Chờ Ban Tổ Chức phê duyệt. Vui lòng quay lại sau khi nhận được email kích hoạt mật khẩu.");
        }

        String dbPassword = employee.getPassword();
        if (dbPassword == null || !passwordEncoder.matches(request.getPassword(), dbPassword)) {
            throw new IllegalArgumentException(BAD_CREDENTIALS);
        }

        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", buildAuthResponse(employee)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        String email = jwtTokenProvider.validateRefreshToken(request.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Refresh Token không hợp lệ, đã hết hạn hoặc đã bị thu hồi."));

        Employee employee = employeeRepository.findByEmail(email)
                .filter(e -> e.getApprovalStatus() == ApprovalStatus.ACCEPTED)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không còn hợp lệ."));

        return ResponseEntity.ok(ApiResponse.ok("Cấp lại AccessToken thành công!", buildAuthResponse(employee)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout() {
        SecurityUtils.currentEmail().ifPresent(jwtTokenProvider::revokeRefreshToken);
        return ResponseEntity.ok(ApiResponse.ok("Đăng xuất và thu hồi Refresh Token thành công.", ""));
    }

    /**
     * Reset password. The caller must first request an OTP (/send-otp) and prove
     * ownership of the mailbox by sending that code here.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@RequestParam("email") String email,
                                                              @RequestParam("otpCode") String otpCode) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập địa chỉ email của bạn.");
        }
        String cleanEmail = email.trim().toLowerCase();
        otpService.verifyOtp(cleanEmail, otpCode);
        otpService.consumeVerifiedEmail(cleanEmail);

        Employee employee = employeeRepository.findByEmail(cleanEmail)
                .filter(e -> e.getApprovalStatus() == ApprovalStatus.ACCEPTED)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản đã được phê duyệt với email: " + cleanEmail));

        String rawPassword = PasswordGenerator.generate();
        employee.setPassword(passwordEncoder.encode(rawPassword));
        employeeRepository.save(employee);
        jwtTokenProvider.revokeRefreshToken(cleanEmail);

        emailService.sendForgotPasswordEmail(
                employee.getEmail(),
                employee.getFullName(),
                employee.getEmployeeCode(),
                rawPassword
        );

        return ResponseEntity.ok(ApiResponse.ok("Mật khẩu mới đã được gửi tới email " + cleanEmail + ". Vui lòng kiểm tra hộp thư!", cleanEmail));
    }

    private AuthResponse buildAuthResponse(Employee employee) {
        String accessToken = jwtTokenProvider.generateAccessToken(employee);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(jwtTokenProvider.generateRefreshToken(employee))
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
    }
}
