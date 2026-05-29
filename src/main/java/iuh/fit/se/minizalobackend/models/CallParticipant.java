package iuh.fit.se.minizalobackend.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "call_participants",
        indexes = {
                @Index(name = "idx_call_participants_session", columnList = "call_session_id"),
                @Index(name = "idx_call_participants_user", columnList = "user_id")
        }
)
public class CallParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "call_session_id", nullable = false)
    private CallSession callSession;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ECallParticipantStatus status = ECallParticipantStatus.INVITED;

    @Column(name = "is_delivered", nullable = false)
    private boolean delivered = false;

    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;

    @PrePersist
    protected void onCreate() {
        if (status == null) status = ECallParticipantStatus.INVITED;
    }
}

