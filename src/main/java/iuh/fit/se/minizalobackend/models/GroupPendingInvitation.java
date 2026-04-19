package iuh.fit.se.minizalobackend.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Người được mời / xin vào nhóm khi bật requireApproval — chờ trưởng/phó duyệt.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "group_pending_invitations",
        uniqueConstraints = @UniqueConstraint(name = "uk_pending_group_candidate", columnNames = {"group_id", "candidate_user_id"}))
public class GroupPendingInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ChatRoom group;

    /** Người được thêm vào nhóm sau khi duyệt */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_user_id", nullable = false)
    private User candidateUser;

    /** Người mời (null nếu tham gia qua link) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by")
    private User invitedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
