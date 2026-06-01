package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.services.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Gửi SMS OTP thông qua eSMS.vn REST API (Java 17 HttpClient).
 *
 * Đăng ký tài khoản miễn phí (50 SMS thử) tại: https://esms.vn
 * Cấu hình cần thiết trong .env:
 *   ESMS_API_KEY=your_api_key
 *   ESMS_SECRET_KEY=your_secret_key
 *
 * Nếu chưa cấu hình (để trống), SMS sẽ bị BỎ QUA và OTP chỉ được log ra console.
 * Điều này giúp môi trường local dev vẫn chạy được bình thường.
 *
 * SmsType "4" = kênh OTP (nhanh nhất, ưu tiên cao nhất, 1-5 giây đến VN).
 */
@Service
@Slf4j
public class EsmsSmsServiceImpl implements SmsService {

    private static final String ESMS_API_URL = "https://rest.esms.vn/MainService.svc/json/SendMultipleMessage_V4_post_json/";

    @Value("${esms.api.key:}")
    private String apiKey;

    @Value("${esms.secret.key:}")
    private String secretKey;

    @Value("${esms.sms.type:4}")
    private String smsType;

    @Value("${esms.brandname:}")
    private String brandname;

    @Value("${esms.content.template:MiniZalo - Ma OTP cua ban la: {OTP}. Co hieu luc trong 5 phut. Khong chia se ma nay cho ai.}")
    private String contentTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public void sendOtpSms(String toPhone, String otp) {
        // Kiểm tra cấu hình ESMS — nếu chưa set thì chỉ log (local dev)
        if (apiKey == null || apiKey.isBlank()
                || secretKey == null || secretKey.isBlank()) {
            log.warn("=== [SMS - ESMS CHƯA CẤU HÌNH] Số: {} | OTP: {} | " +
                    "Vui lòng thêm ESMS_API_KEY và ESMS_SECRET_KEY vào .env ===",
                    toPhone, otp);
            return;
        }

        String normalizedPhone = normalizeVnPhone(toPhone);
        String content = contentTemplate.replace("{OTP}", otp);

        // Build JSON payload
        String jsonBody = buildJsonPayload(normalizedPhone, content);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ESMS_API_URL))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String body = response.body();

            if (status >= 200 && status < 300) {
                // eSMS trả về CodeResult: "100" = thành công
                if (body.contains("\"CodeResult\":\"100\"")) {
                    log.info("[eSMS] OTP gửi thành công đến {}", normalizedPhone);
                } else {
                    log.error("[eSMS] Gửi OTP thất bại đến {}: {}", normalizedPhone, body);
                    throw new RuntimeException("Gửi SMS OTP thất bại. Vui lòng thử lại hoặc chọn kênh Email.");
                }
            } else {
                log.error("[eSMS] HTTP {} khi gửi OTP đến {}: {}", status, normalizedPhone, body);
                throw new RuntimeException("Gửi SMS OTP thất bại (HTTP " + status + "). Vui lòng thử lại.");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("[eSMS] Lỗi kết nối khi gửi OTP đến {}: {}", normalizedPhone, e.getMessage());
            throw new RuntimeException("Gửi SMS OTP thất bại. Vui lòng thử lại hoặc chọn kênh Email.", e);
        }
    }

    /**
     * Build JSON payload theo định dạng eSMS API v4.
     * SmsType = "4" → kênh OTP, ưu tiên cao nhất, nhanh nhất.
     */
    private String buildJsonPayload(String phone, String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append(String.format("\"ApiKey\":\"%s\",", escapeJson(apiKey)));
        sb.append(String.format("\"Content\":\"%s\",", escapeJson(content)));
        sb.append(String.format("\"Phone\":\"%s\",", escapeJson(phone)));
        sb.append(String.format("\"SecretKey\":\"%s\",", escapeJson(secretKey)));
        sb.append(String.format("\"SmsType\":\"%s\",", escapeJson(smsType)));
        
        if (brandname != null && !brandname.isBlank()) {
            sb.append(String.format("\"Brandname\":\"%s\",", escapeJson(brandname)));
        }
        
        sb.append("\"IsUnicode\":\"0\",");
        sb.append("\"Sandbox\":\"0\"");
        sb.append("}");
        return sb.toString();
        return String.format(
                "{" +
                "\"ApiKey\":\"%s\"," +
                "\"Content\":\"%s\"," +
                "\"Phone\":\"%s\"," +
                "\"SecretKey\":\"%s\"," +
                "\"SmsType\":\"8\"," +
                "\"IsUnicode\":\"0\"," +
                "\"Sandbox\":\"0\"" +
                "}",
                escapeJson(apiKey),
                escapeJson(content),
                escapeJson(phone),
                escapeJson(secretKey)
        );
    }

    /**
     * Chuẩn hoá số điện thoại VN: 0xxxxxxxxx → 84xxxxxxxxx (eSMS không dùng dấu +).
     */
    private String normalizeVnPhone(String phone) {
        String trimmed = (phone == null) ? "" : phone.trim();
        if (trimmed.startsWith("0") && trimmed.length() == 10) {
            return "84" + trimmed.substring(1);
        }
        if (trimmed.startsWith("+84")) {
            return trimmed.substring(1); // bỏ dấu +
        }
        if (trimmed.startsWith("84") && trimmed.length() == 11) {
            return trimmed;
        }
        return trimmed;
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
    }
}
