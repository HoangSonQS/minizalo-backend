package iuh.fit.se.minizalobackend.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "group_settings")
public class GroupSettings {
    @Id
    @Column(name = "group_id")
    private UUID groupId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "group_id")
    private ChatRoom group;

    // Cho phép thành viên:
    @Column(nullable = false)
    @Builder.Default
    private boolean allowMemberChangeName = true;
    
    @Column(nullable = false)
    @Builder.Default
    private boolean allowMemberPin = true;
    
    @Column(nullable = false)
    @Builder.Default
    private boolean allowMemberCreatePoll = true;
    
    @Column(nullable = false)
    @Builder.Default
    private boolean allowMemberSendMessage = true;

    // Thiết lập nhóm:
    @Column(nullable = false)
    @Builder.Default
    private boolean requireApproval = false;
    
    @Column(nullable = false)
    @Builder.Default
    private boolean allowNewMemberReadHistory = true;
    
    @Column(nullable = false)
    @Builder.Default
    private boolean allowJoinByLink = true;

    @Column(unique = true)
    private String joinLink; // UUID-based join link token, e.g., a random UUID string

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
