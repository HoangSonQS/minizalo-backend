package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@Slf4j
@RequiredArgsConstructor
public class GmailEmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Override
    public void sendOtpEmail(String toEmail, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("MiniZalo - Mã xác thực OTP");
            helper.setText(buildOtpHtml(otp), true);

            mailSender.send(message);
            log.info("OTP email sent to {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Gửi email OTP thất bại. Vui lòng thử lại.", e);
        }
    }

    private String buildOtpHtml(String otp) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:24px;border:1px solid #e0e0e0;border-radius:12px">
              <h2 style="color:#0068FF;text-align:center;margin-bottom:8px">MiniZalo</h2>
              <p style="text-align:center;color:#333;font-size:15px">Mã xác thực (OTP) của bạn là:</p>
              <div style="text-align:center;margin:24px 0">
                <span style="font-size:32px;font-weight:bold;letter-spacing:8px;color:#0068FF">%s</span>
              </div>
              <p style="text-align:center;color:#666;font-size:13px">Mã có hiệu lực trong <b>5 phút</b>. Vui lòng không chia sẻ mã này cho bất kỳ ai.</p>
            </div>
            """.formatted(otp);
    }
}
