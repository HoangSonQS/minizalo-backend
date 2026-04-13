package iuh.fit.se.minizalobackend.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "call_sessions")
public class CallSession {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String channelName; // conversationId dạng string

    @Column(nullable = false)
    private UUID callerId;

    @Column(nullable = false)
    private UUID receiverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ECallType callType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ECallStatus status;

    private LocalDateTime startedAt; // Thời điểm bắt đầu nhận cuộc gọi
    private LocalDateTime endedAt;   // Thời điểm kết thúc
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Column(name = "is_delivered", nullable = false)
    private boolean delivered = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = ECallStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public long getDurationSeconds() {
        if (startedAt != null && endedAt != null) {
            return Duration.between(startedAt, endedAt).getSeconds();
        }
        return 0;
    }
}
