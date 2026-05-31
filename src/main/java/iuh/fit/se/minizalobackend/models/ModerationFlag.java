package iuh.fit.se.minizalobackend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

@Data
@Entity
@Table(name = "moderation_flags")
@NoArgsConstructor
@AllArgsConstructor
public class ModerationFlag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String messageId;
    private String roomId;
    private String senderId;

    // For story reports
    private String targetType; // MESSAGE or STORY
    private String targetId;   // storyId (createdAt) for STORY, messageId for MESSAGE
    private String reporterId; // userId of the reporter (for manual reports)
    
    @Column(columnDefinition = "TEXT")
    private String content;

    private String reason; // e.g. INAPPROPRIATE, SPAM, VIOLENCE, AUTO_AI
    private String status; // PENDING, APPROVED, DELETED

    private Instant flaggedAt = Instant.now();
}
