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

    /** Conversation (chat room) id. */
    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(nullable = false)
    private UUID callerId;

    /** Receiver for 1-1 calls only. Null for group calls. */
    @Column
    private UUID receiverId;

    /** True if this session is a group call. */
    @Column(nullable = false)
    private boolean groupCall = false;

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

    /**
     * ID của tin nhắn "call log" trong DynamoDB.
     * Dùng cho group call: khi khởi tạo → log 1 tin STARTED, khi kết thúc → update in-place
     * cùng tin này (không tạo tin mới) để FE chỉ hiển thị 1 bubble duy nhất.
     * Null cho call 1-1 (1-1 chỉ sinh ra 1 tin log ở thời điểm kết thúc).
     */
    @Column(name = "message_id")
    private String messageId;

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
