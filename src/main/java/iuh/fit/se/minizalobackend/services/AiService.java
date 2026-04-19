package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.models.MessageDynamo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiService {

    @Value("${gemini.api.key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    public String summarizeChat(List<MessageDynamo> messages) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            log.error("GEMINI_API_KEY is not configured.");
            return "Hệ thống AI chưa được cấu hình. Vui lòng liên hệ quản trị viên.";
        }

        if (messages.isEmpty()) {
            return "Không có tin nhắn nào trong khoảng thời gian này.";
        }

        // Format all messages to readable strings, interpreting their types
        String transcript = messages.stream()
                .map(m -> {
                    String prefix = m.getSenderName() + " (" + m.getCreatedAt() + "): ";
                    
                    if (m.isRecalled()) {
                        return prefix + "[Đã thu hồi tin nhắn]";
                    }
                    
                    if ("IMAGE".equalsIgnoreCase(m.getType())) {
                        return prefix + "[Gửi một hình ảnh]";
                    }
                    if ("VIDEO".equalsIgnoreCase(m.getType())) {
                        return prefix + "[Gửi một đoạn video]";
                    }
                    if ("AUDIO".equalsIgnoreCase(m.getType())) {
                        return prefix + "[Gửi một tin nhắn thoại]";
                    }
                    if ("FILE".equalsIgnoreCase(m.getType())) {
                        String fileName = "tệp tin";
                        if (m.getAttachments() != null && !m.getAttachments().isEmpty() && m.getAttachments().get(0).getFilename() != null) {
                            fileName = m.getAttachments().get(0).getFilename();
                        }
                        return prefix + "[Gửi đính kèm: " + fileName + "]";
                    }
                    if ("SYSTEM".equalsIgnoreCase(m.getType())) {
                        return "[HỆ THỐNG]: " + m.getContent();
                    }
                    if ("CALL".equalsIgnoreCase(m.getType())) {
                        return prefix + "[Cuộc gọi thoại/video]";
                    }
                    
                    // Fallback to text
                    return prefix + m.getContent();
                })
                .collect(Collectors.joining("\n"));

        if (transcript.isBlank()) {
            return "Không có văn bản nào để tóm tắt trong khoảng thời gian này.";
        }

        String prompt = "Dưới đây là một cuộc hội thoại trong ứng dụng nhắn tin. " +
                "Hãy đọc và tóm tắt lại các ý chính đã được trao đổi một cách ngắn gọn, súc tích " +
                "và có cấu trúc rõ ràng (bằng tiếng Việt, sử dụng các gạch đầu dòng).\n\n" +
                "Đoạn hội thoại:\n" + transcript;

        // Tạo JSON body theo chuẩn Google Gemini 1.5 Flash
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> contents = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();
        parts.put("text", prompt);
        contents.put("parts", List.of(parts));
        requestBody.put("contents", List.of(contents));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            String url = GEMINI_API_URL + geminiApiKey;
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

            // Phân tích kết quả JSON trả về
            if (response != null && response.containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<String, Object> candidate = candidates.get(0);
                    Map<String, Object> contentMap = (Map<String, Object>) candidate.get("content");
                    List<Map<String, Object>> partsList = (List<Map<String, Object>>) contentMap.get("parts");
                    return (String) partsList.get(0).get("text");
                }
            }
            return "Không có nội dung tóm tắt từ AI.";
        } catch (Exception e) {
            log.error("Lỗi khi kết nối với Gemini API: {}", e.getMessage());
            return "Đã xảy ra lỗi khi yêu cầu AI: " + e.getMessage();
        }
    }
}
