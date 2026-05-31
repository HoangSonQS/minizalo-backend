package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.models.ModerationFlag;
import iuh.fit.se.minizalobackend.repository.ModerationFlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationService {
    
    private final AiService aiService;
    private final ModerationFlagRepository moderationFlagRepository;

    @Async
    public void scanAndFlagMessage(String messageId, String roomId, String senderId, String content) {
        if (content == null || content.isBlank()) return;
        
        try {
            boolean isToxic = aiService.isToxicMessage(content);
            if (isToxic) {
                ModerationFlag flag = new ModerationFlag();
                flag.setMessageId(messageId);
                flag.setRoomId(roomId);
                flag.setSenderId(senderId);
                flag.setContent(content);
                flag.setReason("AI Flagged as Toxic/Inappropriate");
                flag.setStatus("PENDING");
                flag.setFlaggedAt(Instant.now());
                
                moderationFlagRepository.save(flag);
                log.info("Message {} in room {} flagged by AI moderation", messageId, roomId);
            }
        } catch (Exception e) {
            log.warn("AI Moderation failed for message {}: {}", messageId, e.getMessage());
        }
    }
}
