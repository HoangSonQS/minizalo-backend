package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.models.ChatSummary;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.repository.ChatSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final ChatSummaryRepository chatSummaryRepository;

    @Lazy
    @Autowired
    private SystemConfigService systemConfigService;

    @Value("${gemini.api.key:${GEMINI_API_KEY:}}")
    private String geminiApiKey1;

    @Value("${gemini.api.key2:${GEMINI_API_KEY_2:}}")
    private String geminiApiKey2;

    @Value("${gemini.api.key3:${GEMINI_API_KEY_3:}}")
    private String geminiApiKey3;

    @Value("${gemini.api.key4:${GEMINI_API_KEY_4:}}")
    private String geminiApiKey4;

    @Value("${gemini.api.key5:${GEMINI_API_KEY_5:}}")
    private String geminiApiKey5;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    @SuppressWarnings("unchecked")
    public String summarizeChat(String roomId, List<MessageDynamo> messages, boolean isUnreadOnly) {
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
                        if (m.getAttachments() != null && !m.getAttachments().isEmpty()
                                && m.getAttachments().get(0).getFilename() != null) {
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

        String prompt;
        if (isUnreadOnly) {
            prompt = "Bạn là trợ lý ảo MiniZalo. Hãy thực hiện 'Tóm tắt nhanh' (Quick Catch-up) đoạn hội thoại bên dưới.\n" +
                    "Đoạn hội thoại này CHỈ bao gồm các tin nhắn MỚI mà người dùng chưa đọc.\n\n" +
                    "Yêu cầu:\n" +
                    "1. Tập trung tuyệt đối vào diễn biến MỚI NHẤT.\n" +
                    "2. KHÔNG nhắc lại các nội dung cũ hoặc lịch sử trước đó.\n" +
                    "3. Dùng ngôn ngữ cực kỳ cô đọng, súc tích (Bullet points).\n" +
                    "4. Nếu có yêu cầu hối thúc hoặc mốc thời gian quan trọng, hãy nhấn mạnh.\n\n" +
                    "Đoạn hội thoại mới:\n" + transcript;
        } else {
            prompt = "Bạn là một trợ lý ảo thông minh của ứng dụng MiniZalo. " +
                    "Nhiệm vụ của bạn là tóm tắt đoạn hội thoại dưới đây một cách chuyên nghiệp, khách quan và dễ hiểu.\n\n"
                    +
                    "Yêu cầu về định dạng:\n" +
                    "1. 📌 **Chủ đề chính**: Tóm tắt ngắn gọn cuộc thảo luận xoay quanh vấn đề gì.\n" +
                    "2. 💬 **Nội dung chi tiết**: Sử dụng các gạch đầu dòng để liệt kê các ý chính, thông tin quan trọng hoặc các mốc thời gian đáng chú ý.\n"
                    +
                    "3. ✅ **Kết luận/Hành động tiếp theo**: Nếu có các quyết định đã được đưa ra hoặc các công việc cần làm tiếp theo, hãy liệt kê rõ.\n\n"
                    +
                    "Lưu ý:\n" +
                    "- Sử dụng tiếng Việt tự nhiên, lịch sự.\n" +
                    "- Nếu có các tệp đính kèm (hình ảnh, video, file), hãy nhắc đến chúng nếu chúng quan trọng đối với ngữ cảnh.\n"
                    +
                    "- Giữ độ dài vừa phải, không quá lan man.\n\n" +
                    "Đoạn hội thoại:\n" + transcript;
        }

        // Tạo JSON body theo chuẩn Google Gemini 1.5 Flash
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> contents = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();
        parts.put("text", prompt);
        contents.put("parts", List.of(parts));
        requestBody.put("contents", List.of(contents));

        List<String> apiKeys = java.util.Arrays.asList(
                geminiApiKey1, geminiApiKey2, geminiApiKey3, geminiApiKey4, geminiApiKey5).stream()
                .filter(k -> k != null && !k.isBlank()).collect(Collectors.toList());

        if (apiKeys.isEmpty()) {
            log.error("No GEMINI_API_KEYs are configured.");
            return "Hệ thống AI chưa được cấu hình. Vui lòng liên hệ quản trị viên.";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        Exception lastException = null;

        for (int i = 0; i < apiKeys.size(); i++) {
            String currentKey = apiKeys.get(i);
            log.info("Đang thử với API Key thứ {}/{}", (i + 1), apiKeys.size());

            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    String url = GEMINI_API_URL + currentKey;
                    Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

                    if (response != null && response.containsKey("candidates")) {
                        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                        if (!candidates.isEmpty()) {
                            Map<String, Object> candidate = candidates.get(0);
                            Map<String, Object> contentMap = (Map<String, Object>) candidate.get("content");
                            List<Map<String, Object>> partsList = (List<Map<String, Object>>) contentMap.get("parts");
                            String summaryResult = (String) partsList.get(0).get("text");
                            
                            // Lưu kết quả tóm tắt vào lịch sử (Hết hạn sau 5 ngày)
                            try {
                                ChatSummary summary = new ChatSummary();
                                summary.setRoomId(roomId);
                                summary.setCreatedAt(Instant.now().toString());
                                summary.setSummaryId(UUID.randomUUID().toString());
                                summary.setContent(summaryResult);
                                // TTL: Hiện tại + 5 ngày (đơn vị epoch seconds)
                                long ttlSecs = Instant.now().plus(5, ChronoUnit.DAYS).getEpochSecond();
                                summary.setTtl(ttlSecs);
                                
                                chatSummaryRepository.save(summary);
                                log.info("Đã lưu lịch sử tóm tắt cho phòng {}, TTL: {}", roomId, ttlSecs);
                            } catch (Exception saveEx) {
                                log.error("Lỗi khi lưu lịch sử tóm tắt: {}", saveEx.getMessage());
                            }

                            return summaryResult;
                        }
                    }
                } catch (Exception e) {
                    lastException = e;
                    log.warn("API Key {} - Lần thử {} thất bại: {}", (i + 1), attempt, e.getMessage());

                    // Nếu là lỗi 429 (Hết hạn mức) thì chuyển key ngay lập tức không cần thử lần 2
                    // của key đó
                    if (e.getMessage() != null && e.getMessage().contains("429")) {
                        log.warn("API Key {} đã hết hạn mức (429). Chuyển sang Key tiếp theo...", (i + 1));
                        break;
                    }

                    // Nếu là lỗi khác (như 503), đợi một chút rồi thử lại lần 2 của cùng key
                    if (attempt < 2) {
                        try {
                            Thread.sleep(1500);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }

        log.error("Tất cả {} API Keys đều thất bại. Lỗi cuối cùng: {}", apiKeys.size(),
                lastException != null ? lastException.getMessage() : "Unknown");

        if (lastException != null) {
            String msg = lastException.getMessage();
            if (msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED")) {
                return "Tất cả các API Key hiện tại đều đã hết hạn mức sử dụng (429). Vui lòng thử lại sau hoặc nâng cấp gói dịch vụ.";
            }
            if (msg.contains("503") || msg.contains("UNAVAILABLE")) {
                return "Hệ thống AI hiện đang quá tải hoặc gặp sự cố kỹ thuật. Vui lòng thử lại sau giây lát.";
            }
        }
        return "Đã xảy ra lỗi khi yêu cầu AI sau khi thử tất cả các Key dự phòng.";
    }

    public List<ChatSummary> getSummaryHistory(String roomId) {
        return chatSummaryRepository.getSummariesByRoomId(roomId);
    }

    public String askPersona(String persona, String question) {
        String prompt = "Bạn là một chuyên gia hàng đầu về " + persona + ".\n" +
                "QUY TẮC QUAN TRỌNG: Bạn CHỈ ĐƯỢC PHÉP trả lời các câu hỏi hoặc thảo luận về các vấn đề có liên quan trực tiếp đến " + persona + ".\n" +
                "Nếu câu hỏi của người dùng KHÔNG liên quan đến " + persona + ", bạn PHẢI từ chối trả lời một cách lịch sự và nhắc nhở người dùng rằng bạn chỉ là chuyên gia về " + persona + ".\n\n" +
                "Câu hỏi của người dùng:\n" + question;

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> contents = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();
        parts.put("text", prompt);
        contents.put("parts", List.of(parts));
        requestBody.put("contents", List.of(contents));

        // Enable Google Search Grounding
        Map<String, Object> googleSearchTool = new HashMap<>();
        googleSearchTool.put("googleSearch", new HashMap<>());
        requestBody.put("tools", List.of(googleSearchTool));

        List<String> apiKeys = java.util.Arrays.asList(
                geminiApiKey1, geminiApiKey2, geminiApiKey3, geminiApiKey4, geminiApiKey5).stream()
                .filter(k -> k != null && !k.isBlank()).collect(Collectors.toList());

        if (apiKeys.isEmpty()) {
            return "Hệ thống AI chưa được cấu hình. Vui lòng liên hệ quản trị viên.";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        for (int i = 0; i < apiKeys.size(); i++) {
            String currentKey = apiKeys.get(i);
            try {
                String url = GEMINI_API_URL + currentKey;
                Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

                if (response != null && response.containsKey("candidates")) {
                    List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                    if (!candidates.isEmpty()) {
                        Map<String, Object> candidate = candidates.get(0);
                        Map<String, Object> contentMap = (Map<String, Object>) candidate.get("content");
                        List<Map<String, Object>> partsList = (List<Map<String, Object>>) contentMap.get("parts");
                        return (String) partsList.get(0).get("text");
                    }
                }
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    continue;
                }
            }
        }
        return "Hiện tại tất cả các chuyên gia AI đều đang bận. Vui lòng thử lại sau.";
    }

    public String translateText(String text, String targetLanguage) {
        String prompt = "Bạn là một biên dịch viên chuyên nghiệp. Hãy dịch đoạn văn bản sau sang tiếng " + targetLanguage + ".\n" +
                "Chỉ trả về bản dịch, không giải thích gì thêm.\n\n" +
                "Văn bản:\n" + text;
        return callGemini(prompt, false);
    }

    public String improveText(String text) {
        String prompt = "Bạn là một biên tập viên chuyên nghiệp. Hãy sửa lỗi chính tả, cải thiện văn phong và làm cho đoạn văn bản sau trở nên chuyên nghiệp, trôi chảy hơn.\n" +
                "Chỉ trả về văn bản đã được chỉnh sửa, không giải thích gì thêm.\n\n" +
                "Văn bản gốc:\n" + text;
        return callGemini(prompt, false);
    }

    public String extractEvents(String roomId, List<MessageDynamo> messages) {
        if (messages.isEmpty()) {
            return "Không có dữ liệu tin nhắn để trích xuất.";
        }

        String transcript = messages.stream()
                .map(m -> m.getSenderName() + " (" + m.getCreatedAt() + "): " + m.getContent())
                .collect(Collectors.joining("\n"));

        String prompt = "Bạn là trợ lý AI thông minh. Hãy đọc kỹ đoạn hội thoại sau và trích xuất TOÀN BỘ các lịch hẹn, sự kiện, ngày tháng, thời gian hoặc deadline được nhắc đến.\n" +
                "Trình bày kết quả dưới dạng danh sách rõ ràng (Bullet points). Nếu không tìm thấy sự kiện nào, hãy trả lời 'Không tìm thấy lịch hẹn hoặc sự kiện nào trong đoạn hội thoại này.'\n\n" +
                "Hội thoại:\n" + transcript;
        return callGemini(prompt, false);
    }

    private String callGemini(String prompt, boolean useSearch) {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> contents = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();
        parts.put("text", prompt);
        contents.put("parts", List.of(parts));
        requestBody.put("contents", List.of(contents));

        if (useSearch) {
            Map<String, Object> googleSearchTool = new HashMap<>();
            googleSearchTool.put("googleSearch", new HashMap<>());
            requestBody.put("tools", List.of(googleSearchTool));
        }

        List<String> apiKeys = java.util.Arrays.asList(
                geminiApiKey1, geminiApiKey2, geminiApiKey3, geminiApiKey4, geminiApiKey5).stream()
                .filter(k -> k != null && !k.isBlank()).collect(Collectors.toList());

        if (apiKeys.isEmpty()) {
            return "Hệ thống AI chưa được cấu hình. Vui lòng liên hệ quản trị viên.";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        for (int i = 0; i < apiKeys.size(); i++) {
            String currentKey = apiKeys.get(i);
            try {
                String url = GEMINI_API_URL + currentKey;
                Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

                if (response != null && response.containsKey("candidates")) {
                    List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                    if (!candidates.isEmpty()) {
                        Map<String, Object> candidate = candidates.get(0);
                        Map<String, Object> contentMap = (Map<String, Object>) candidate.get("content");
                        List<Map<String, Object>> partsList = (List<Map<String, Object>>) contentMap.get("parts");
                        return (String) partsList.get(0).get("text");
                    }
                }
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    continue;
                }
            }
        }
        return "Hiện tại hệ thống AI đang quá tải. Vui lòng thử lại sau.";
    }

    public String transcribeAudio(String base64Audio, String mimeType) {
        // Build multimodal request with audio inline data
        Map<String, Object> requestBody = new HashMap<>();

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", "Hãy nghe đoạn audio này và chuyển thành văn bản (speech-to-text). " +
                "Chỉ trả về nội dung văn bản thuần túy, KHÔNG giải thích, KHÔNG thêm dấu ngoặc kép, KHÔNG format markdown.");

        Map<String, Object> audioPart = new HashMap<>();
        Map<String, Object> inlineData = new HashMap<>();
        inlineData.put("mimeType", mimeType != null ? mimeType : "audio/mp4");
        inlineData.put("data", base64Audio);
        audioPart.put("inlineData", inlineData);

        Map<String, Object> contents = new HashMap<>();
        contents.put("parts", List.of(textPart, audioPart));
        requestBody.put("contents", List.of(contents));

        List<String> apiKeys = java.util.Arrays.asList(
                geminiApiKey1, geminiApiKey2, geminiApiKey3, geminiApiKey4, geminiApiKey5).stream()
                .filter(k -> k != null && !k.isBlank()).collect(Collectors.toList());

        if (apiKeys.isEmpty()) {
            return "";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        for (int i = 0; i < apiKeys.size(); i++) {
            String currentKey = apiKeys.get(i);
            try {
                String url = GEMINI_API_URL + currentKey;
                Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

                if (response != null && response.containsKey("candidates")) {
                    List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                    if (!candidates.isEmpty()) {
                        Map<String, Object> candidate = candidates.get(0);
                        Map<String, Object> contentMap = (Map<String, Object>) candidate.get("content");
                        List<Map<String, Object>> partsList = (List<Map<String, Object>>) contentMap.get("parts");
                        return (String) partsList.get(0).get("text");
                    }
                }
            } catch (Exception e) {
                log.warn("Transcribe audio - Key {} failed: {}", (i + 1), e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    continue;
                }
            }
        }
        return "";
    }

    public boolean isToxicMessage(String text) {
        // Check if moderation is enabled
        if (systemConfigService != null && !systemConfigService.isEnabled(SystemConfigService.KEY_AI_MODERATION_ENABLED)) {
            return false;
        }

        // Fast path: keyword blocklist check (no AI cost)
        if (systemConfigService != null) {
            String keywords = systemConfigService.getValue(SystemConfigService.KEY_AI_MODERATION_KEYWORDS, "");
            if (!keywords.isBlank()) {
                String textLower = text.toLowerCase();
                for (String kw : keywords.split(",")) {
                    String trimmed = kw.trim().toLowerCase();
                    if (!trimmed.isEmpty() && textLower.contains(trimmed)) {
                        log.info("Keyword blocklist matched '{}' in message", trimmed);
                        return true;
                    }
                }
            }
        }

        // AI inference with dynamic prompt
        String basePrompt = systemConfigService != null
                ? systemConfigService.getValue(SystemConfigService.KEY_AI_MODERATION_PROMPT,
                    "Bạn là hệ thống kiểm duyệt tự động. Hãy kiểm tra đoạn tin nhắn sau xem có chứa ngôn từ độc hại, chửi bậy, lăng mạ, lừa đảo, đa cấp, khiêu dâm, hoặc vi phạm tiêu chuẩn cộng đồng không.\nChỉ trả lời chính xác bằng một từ 'YES' (nếu vi phạm) hoặc 'NO' (nếu an toàn), không giải thích thêm.\n\nTin nhắn:\n")
                : "Bạn là hệ thống kiểm duyệt tự động. Hãy kiểm tra đoạn tin nhắn sau xem có chứa ngôn từ độc hại, chửi bậy, lăng mạ, lừa đảo, đa cấp, khiêu dâm, hoặc vi phạm tiêu chuẩn cộng đồng không.\nChỉ trả lời chính xác bằng một từ 'YES' (nếu vi phạm) hoặc 'NO' (nếu an toàn), không giải thích thêm.\n\nTin nhắn:\n";

        String sensitivity = systemConfigService != null
                ? systemConfigService.getValue(SystemConfigService.KEY_AI_MODERATION_SENSITIVITY, "MEDIUM")
                : "MEDIUM";
        if ("HIGH".equalsIgnoreCase(sensitivity)) {
            basePrompt += "[Chế độ nhạy cảm CAO: Flag ngay cả khi chỉ ngầm có dấu hiệu vi phạm]\n";
        } else if ("LOW".equalsIgnoreCase(sensitivity)) {
            basePrompt += "[Chế độ nhạy cảm THẤP: Chỉ flag khi nội dung rõ ràng vi phạm]\n";
        }

        String prompt = basePrompt + text;
        String result = callGemini(prompt, false);
        return result != null && result.trim().toUpperCase().contains("YES");
    }
}
