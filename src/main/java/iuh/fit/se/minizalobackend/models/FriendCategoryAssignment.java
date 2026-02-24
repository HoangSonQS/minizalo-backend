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
        name = "friend_category_assignments",
        uniqueConstraints = {
                // Một owner chỉ gán 1 category cho 1 target user
                @UniqueConstraint(name = "uk_owner_target", columnNames = {"owner_user_id", "target_user_id"})
        }
)
public class FriendCategoryAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // Owner: user đang đăng nhập (người phân loại)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    // Target: user bạn bè bị gán phân loại
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User target;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private FriendCategory category;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

