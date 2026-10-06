package com.landing.page.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.admin.notify-email:}")
    private String adminEmail;

    /**
     * Send 6-digit OTP verification code to employee email
     */
    @Async
    public void sendOtpEmail(String toEmail, String otpCode) {
        String subject = "[CTIN Treasure Hunt] Mã OTP xác thực Email của bạn";
        String body = String.format(
                "Kính gửi bạn,\n\n" +
                "Mã OTP xác thực địa chỉ Email của bạn tại chương trình CTIN Treasure Hunt 2026 là:\n\n" +
                "🔑 MÃ OTP:  %s\n\n" +
                "(Mã OTP này có hiệu lực trong vòng 05 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai khác).\n\n" +
                "Trân trọng,\n" +
                "Ban Tổ Chức CTIN Treasure Hunt 2026",
                otpCode
        );

        sendEmail(toEmail, subject, body);
    }

    /**
     * Send Account credentials to employee's email
     */
    @Async
    public void sendAccountCredentialsEmail(String toEmail, String fullName, String employeeCode, String password, String unitName) {
        String subject = "[CTIN Treasure Hunt] Thông tin tài khoản đăng nhập của bạn";
        String body = String.format(
                "Kính gửi %s,\n\n" +
                "Chúc mừng bạn đã đăng ký thành công chương trình CTIN Treasure Hunt (Kỷ niệm 25 năm CTIN)!\n" +
                "Ban Tổ Chức đã phê duyệt tài khoản cá nhân của bạn với thông tin đăng nhập như sau:\n\n" +
                "--------------------------------------------------\n" +
                "• Email đăng nhập: %s\n" +
                "• Mã nhân viên: %s\n" +
                "• Mật khẩu truy cập: %s\n" +
                "• Đơn vị: %s\n" +
                "--------------------------------------------------\n\n" +
                "Bạn có thể truy cập website %s bấm 'Đăng nhập' để nộp bài và xem Bảng xếp hạng.\n\n" +
                "Trân trọng,\n" +
                "Ban Tổ Chức CTIN Treasure Hunt 2026",
                fullName, toEmail, employeeCode, password, unitName, frontendUrl
        );

        sendEmail(toEmail, subject, body);
    }

    /**
     * Send Forgot Password email with account credentials to employee's email
     */
    @Async
    public void sendForgotPasswordEmail(String toEmail, String fullName, String employeeCode, String password) {
        String subject = "[CTIN Treasure Hunt] Khôi phục thông tin mật khẩu của bạn";
        String body = String.format(
                "Kính gửi %s,\n\n" +
                "Ban Tổ Chức nhận được yêu cầu khôi phục mật khẩu đăng nhập của bạn tại CTIN Treasure Hunt 2026.\n\n" +
                "Thông tin tài khoản và mật khẩu mới của bạn như sau:\n" +
                "--------------------------------------------------\n" +
                "• Email đăng nhập: %s\n" +
                "• Mã nhân viên: %s\n" +
                "• Mật khẩu truy cập: %s\n" +
                "--------------------------------------------------\n\n" +
                "Bạn có thể dùng mật khẩu trên để đăng nhập tại %s.\n" +
                "Vui lòng bảo mật thông tin tài khoản của bạn.\n\n" +
                "Trân trọng,\n" +
                "Ban Tổ Chức CTIN Treasure Hunt 2026",
                fullName, toEmail, employeeCode, password, frontendUrl
        );

        sendEmail(toEmail, subject, body);
    }

    /**
     * Inform employee that the registration request was rejected
     */
    @Async
    public void sendRejectionEmail(String toEmail, String fullName, String reason) {
        String subject = "[CTIN Treasure Hunt] Yêu cầu đăng ký chưa được phê duyệt";
        String reasonLine = (reason != null && !reason.isBlank())
                ? "Lý do: " + reason.trim() + "\n\n"
                : "";
        String body = String.format(
                "Kính gửi %s,\n\n" +
                "Ban Tổ Chức rất tiếc phải thông báo yêu cầu đăng ký tham gia CTIN Treasure Hunt 2026 của bạn chưa được phê duyệt.\n\n" +
                "%s" +
                "Nếu có thắc mắc, vui lòng liên hệ Ban Tổ Chức.\n\n" +
                "Trân trọng,\n" +
                "Ban Tổ Chức CTIN Treasure Hunt 2026",
                fullName, reasonLine
        );

        sendEmail(toEmail, subject, body);
    }

    /**
     * Send notification to Admin that a new user registered
     */
    @Async
    public void sendNotificationToAdmin(String fullName, String email, String employeeCode, String unitName) {
        if (adminEmail == null || adminEmail.isBlank()) {
            return;
        }
        String subject = "[BTC Notification] Nhân sự mới đăng ký: " + fullName;
        String body = String.format(
                "Thông báo Ban Tổ Chức:\n\n" +
                "Hệ thống vừa ghi nhận nhân sự mới gửi yêu cầu đăng ký (đang chờ duyệt):\n" +
                "- Họ tên: %s\n" +
                "- Email công ty: %s\n" +
                "- Mã nhân viên: %s\n" +
                "- Đơn vị: %s\n\n" +
                "Vui lòng vào trang quản trị %s/admin để phê duyệt.\n",
                fullName, email, employeeCode, unitName, frontendUrl
        );

        sendEmail(adminEmail, subject, body);
    }

    private void sendEmail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (fromEmail != null && !fromEmail.isBlank()) {
                message.setFrom(fromEmail);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("✅ [EmailSender] Email \"{}\" dispatched to: {}", subject, to);
        } catch (Exception e) {
            // Never log the body: it may contain OTP codes or passwords
            log.error("❌ [EmailSender] Could not send email \"{}\" to {}: {}", subject, to, e.getMessage());
        }
    }
}
