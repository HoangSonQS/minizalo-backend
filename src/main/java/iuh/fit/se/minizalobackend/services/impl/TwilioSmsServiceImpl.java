package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.services.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
// @Service đã bị bỏ — EsmsSmsServiceImpl là implementation chính.
// Giữ file này làm tham khảo tích hợp Twilio nếu cần sau này.


import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Gửi SMS OTP thông qua Twilio REST API (Java 17 HttpClient, không cần SDK).
 *
 * Cấu hình cần thiết trong .env:
 *   TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
 *   TWILIO_AUTH_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
 *   TWILIO_FROM_NUMBER=+1xxxxxxxxxx     (số Twilio trial/paid)
 *
 * Nếu chưa cấu hình (để trống), SMS sẽ bị BỎ QUA và OTP chỉ được log ra console.
 * Điều này giúp môi trường local dev vẫn chạy được mà không cần Twilio thật.
 */
@Slf4j
public class TwilioSmsServiceImpl implements SmsService {

    private static final String TWILIO_API_BASE = "https://api.twilio.com/2010-04-01/Accounts/";

    @Value("${twilio.account.sid:}")
    private String accountSid;

    @Value("${twilio.auth.token:}")
    private String authToken;

    @Value("${twilio.from.number:}")
    private String fromNumber;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public void sendOtpSms(String toPhone, String otp) {
        // Kiểm tra cấu hình Twilio — nếu chưa set thì chỉ log (local dev)
        if (accountSid == null || accountSid.isBlank()
                || authToken == null || authToken.isBlank()
                || fromNumber == null || fromNumber.isBlank()) {
            log.warn("=== [SMS - TWILIO CHƯA CẤU HÌNH] Số: {} | OTP: {} | " +
                    "Vui lòng thêm TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_FROM_NUMBER vào .env ===",
                    toPhone, otp);
            return;
        }

        String normalizedPhone = normalizeVnPhone(toPhone);
        String url = TWILIO_API_BASE + accountSid + "/Messages.json";

        // Nội dung SMS (Twilio free trial chỉ hỗ trợ ASCII, tránh dấu tiếng Việt)
        String messageBody = String.format(
                "MiniZalo - Ma xac thuc OTP cua ban la: %s. Co hieu luc trong 5 phut. Khong chia se ma nay cho bat ky ai.",
                otp
        );

        String formBody = "To=" + urlEncode(normalizedPhone)
                + "&From=" + urlEncode(fromNumber)
                + "&Body=" + urlEncode(messageBody);

        String credentials = Base64.getEncoder().encodeToString(
                (accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status >= 200 && status < 300) {
                log.info("[Twilio SMS] OTP gửi thành công đến {}", normalizedPhone);
            } else {
                log.error("[Twilio SMS] Gửi OTP thất bại đến {}: HTTP {} - {}", normalizedPhone, status, response.body());
                throw new RuntimeException("Gửi SMS OTP thất bại (HTTP " + status + "). Vui lòng thử lại hoặc chọn kênh Email.");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Twilio SMS] Lỗi kết nối khi gửi OTP đến {}: {}", normalizedPhone, e.getMessage());
            throw new RuntimeException("Gửi SMS OTP thất bại. Vui lòng thử lại hoặc chọn kênh Email.", e);
        }
    }

    /**
     * Chuẩn hoá số điện thoại VN về định dạng quốc tế (+84...).
     * 0xxxxxxxxx (10 chữ số) → +84xxxxxxxxx
     */
    private String normalizeVnPhone(String phone) {
        String trimmed = (phone == null) ? "" : phone.trim();
        if (trimmed.startsWith("0") && trimmed.length() == 10) {
            return "+84" + trimmed.substring(1);
        }
        if (trimmed.startsWith("+")) {
            return trimmed;
        }
        if (trimmed.startsWith("84") && trimmed.length() == 11) {
            return "+" + trimmed;
        }
        return trimmed;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
