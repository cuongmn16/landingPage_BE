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

    @Value("${spring.mail.username:cuongdohoc@gmail.com}")
    private String fromEmail;

    /**
     * Send 6-digit OTP verification code to employee email
     */
    @Async
    public void sendOtpEmail(String toEmail, String otpCode) {
        log.info("📧 [EmailSender] Sending OTP [{}] to email: {}", otpCode, toEmail);
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
        log.info("📧 [EmailSender] Preparing email for employee: {} ({})", fullName, toEmail);
        String subject = "[CTIN Treasure Hunt] Thông tin tài khoản đăng nhập của bạn";
        String body = String.format(
                "Kính gửi %s,\n\n" +
                "Chúc mừng bạn đã đăng ký thành công chương trình CTIN Treasure Hunt (Kỷ niệm 25 năm CTIN)!\n" +
                "Hệ thống đã tự động tạo tài khoản cá nhân cho bạn với thông tin đăng nhập như sau:\n\n" +
                "--------------------------------------------------\n" +
                "• Email đăng nhập: %s\n" +
                "• Mã nhân viên: %s\n" +
                "• Mật khẩu truy cập: %s\n" +
                "• Đơn vị: %s\n" +
                "--------------------------------------------------\n\n" +
                "Bạn có thể truy cập website http://localhost:5173/ bấm 'Đăng nhập' để nộp bài và xem Bảng xếp hạng.\n\n" +
                "Trân trọng,\n" +
                "Ban Tổ Chức CTIN Treasure Hunt 2026",
                fullName, toEmail, employeeCode, password, unitName
        );

        sendEmail(toEmail, subject, body);
        sendNotificationToAdmin(fullName, toEmail, employeeCode, unitName);
    }

    /**
     * Send Forgot Password email with account credentials to employee's email
     */
    @Async
    public void sendForgotPasswordEmail(String toEmail, String fullName, String employeeCode, String password) {
        log.info("📧 [EmailSender] Sending Forgot Password credentials to employee: {} ({})", fullName, toEmail);
        String subject = "[CTIN Treasure Hunt] Khôi phục thông tin mật khẩu của bạn";
        String body = String.format(
                "Kính gửi %s,\n\n" +
                "Ban Tổ Chức nhận được yêu cầu khôi phục mật khẩu đăng nhập của bạn tại CTIN Treasure Hunt 2026.\n\n" +
                "Thông tin tài khoản và mật khẩu của bạn như sau:\n" +
                "--------------------------------------------------\n" +
                "• Email đăng nhập: %s\n" +
                "• Mã nhân viên: %s\n" +
                "• Mật khẩu truy cập: %s\n" +
                "--------------------------------------------------\n\n" +
                "Bạn có thể dùng mật khẩu trên để đăng nhập tại http://localhost:5173/.\n" +
                "Vui lòng bảo mật thông tin tài khoản của bạn.\n\n" +
                "Trân trọng,\n" +
                "Ban Tổ Chức CTIN Treasure Hunt 2026",
                fullName, toEmail, employeeCode, password
        );

        sendEmail(toEmail, subject, body);
    }

    /**
     * Send notification to Admin that a new user registered
     */
    @Async
    public void sendNotificationToAdmin(String fullName, String email, String employeeCode, String unitName) {
        String adminEmail = "admin@ctin.vn";
        String subject = "[BTC Notification] Nhân sự mới đăng ký: " + fullName;
        String body = String.format(
                "Thông báo Ban Tổ Chức:\n\n" +
                "Hệ thống vừa ghi nhận nhân sự cá nhân mới kích hoạt đăng ký:\n" +
                "- Họ tên: %s\n" +
                "- Email công ty: %s\n" +
                "- Mã nhân viên: %s\n" +
                "- Đơn vị: %s\n",
                fullName, email, employeeCode, unitName
        );

        sendEmail(adminEmail, subject, body);
    }

    private void sendEmail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("✅ [EmailSender] Email successfully dispatched to: {}", to);
        } catch (Exception e) {
            log.warn("⚠️ [EmailSender] Could not send live SMTP email to {} (Local Dev fallback mode): {}", to, e.getMessage());
            log.info("📜 --- EMULATED EMAIL CONTENT TO [{}] ---\nSubject: {}\nBody:\n{}", to, subject, content);
        }
    }
}
