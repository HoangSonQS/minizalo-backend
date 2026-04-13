package iuh.fit.se.minizalobackend.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "friends")
public class Friend {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "friend_id", nullable = false)
    private User friend;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EFriendStatus status;

    private LocalDateTime createdAt;

    /** Lời giới thiệu kèm lời mời (tối đa 150 ký tự), chỉ dùng khi PENDING. */
    @Column(length = 150)
    private String inviteMessage;

    /** Ví dụ: CHAT_WINDOW, PHONE_SEARCH — hiển thị cho người nhận. */
    @Column(length = 32)
    private String inviteSource;

    /**
     * Người gửi chọn: ẩn nhật ký của mình với người nhận (tùy chọn kiểu Zalo).
     * Dùng {@link Boolean} (nullable): bản ghi cũ / cột NULL trong DB sẽ không làm crash khi đọc (primitive boolean + NULL → lỗi runtime).
     */
    @Column(name = "hide_my_timeline_from_friend")
    private Boolean hideMyTimelineFromFriend;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (hideMyTimelineFromFriend == null) {
            hideMyTimelineFromFriend = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (hideMyTimelineFromFriend == null) {
            hideMyTimelineFromFriend = false;
        }
    }
}
