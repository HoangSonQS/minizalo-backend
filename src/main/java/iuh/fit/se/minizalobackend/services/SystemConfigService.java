package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.models.SystemConfig;
import iuh.fit.se.minizalobackend.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository configRepository;

    // Default config keys
    public static final String KEY_AI_MODERATION_ENABLED = "ai.moderation.enabled";
    public static final String KEY_AI_MODERATION_PROMPT = "ai.moderation.prompt";
    public static final String KEY_AI_MODERATION_KEYWORDS = "ai.moderation.keywords";
    public static final String KEY_AI_MODERATION_SENSITIVITY = "ai.moderation.sensitivity";

    @PostConstruct
    public void initDefaults() {
        upsertDefault(KEY_AI_MODERATION_ENABLED, "true",
                "Bật/tắt hệ thống kiểm duyệt AI tự động (true/false)");
        upsertDefault(KEY_AI_MODERATION_PROMPT,
                "Bạn là hệ thống kiểm duyệt tự động. Hãy kiểm tra đoạn tin nhắn sau xem có chứa ngôn từ độc hại, chửi bậy, lăng mạ, lừa đảo, đa cấp, khiêu dâm, hoặc vi phạm tiêu chuẩn cộng đồng không.\nChỉ trả lời chính xác bằng một từ 'YES' (nếu vi phạm) hoặc 'NO' (nếu an toàn), không giải thích thêm.\n\nTin nhắn:\n",
                "Prompt gửi cho AI để kiểm duyệt tin nhắn. Kết thúc prompt bằng dấu xuống dòng để nối với nội dung tin nhắn.");
        upsertDefault(KEY_AI_MODERATION_KEYWORDS,
                "địt,lồn,cặc,đéo,chó chết,đm,dmm,lừa đảo,đa cấp,cờ bạc",
                "Danh sách từ khóa cấm (phân cách bằng dấu phẩy). Tin nhắn chứa bất kỳ từ nào sẽ bị flag mà không cần qua AI.");
        upsertDefault(KEY_AI_MODERATION_SENSITIVITY, "MEDIUM",
                "Độ nhạy cảm của AI: LOW (chỉ nội dung rõ ràng vi phạm), MEDIUM (cân bằng), HIGH (flag ngay cả khi nghi ngờ)");
        log.info("SystemConfig defaults initialized.");
    }

    private void upsertDefault(String key, String defaultValue, String description) {
        if (!configRepository.existsById(key)) {
            configRepository.save(new SystemConfig(key, defaultValue, description, java.time.Instant.now()));
        }
    }

    public List<SystemConfig> getAll() {
        return configRepository.findAll();
    }

    public Optional<SystemConfig> get(String key) {
        return configRepository.findById(key);
    }

    public String getValue(String key, String fallback) {
        return configRepository.findById(key).map(SystemConfig::getValue).orElse(fallback);
    }

    public boolean isEnabled(String key) {
        return "true".equalsIgnoreCase(getValue(key, "true"));
    }

    public SystemConfig save(String key, String value) {
        SystemConfig config = configRepository.findById(key)
                .orElse(new SystemConfig(key, value, null, java.time.Instant.now()));
        config.setValue(value);
        return configRepository.save(config);
    }

    public void saveAll(Map<String, String> updates) {
        updates.forEach(this::save);
    }
}
