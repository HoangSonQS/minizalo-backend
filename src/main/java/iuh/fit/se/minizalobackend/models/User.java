package iuh.fit.se.minizalobackend.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String displayName;
    private String avatarUrl;
    private String coverPhotoUrl;
    private String statusMessage;

    @Column(unique = true)
    private String phone;
    private String gender;
    private LocalDate dateOfBirth;
    private String businessDescription;
    private LocalDateTime lastSeen;
    private Boolean isOnline = false;
    private Boolean accountLocked = false;
    private String fcmToken;
    private Boolean allowPhoneSearch = true; // Mặc định cho phép tìm qua SĐT

    @Enumerated(EnumType.STRING)
    @Column(name = "allow_messages_from", length = 20)
    private EPrivacyAudience allowMessagesFrom = EPrivacyAudience.EVERYONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "allow_calls_from", length = 20)
    private EPrivacyAudience allowCallsFrom = EPrivacyAudience.EVERYONE;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RefreshToken> refreshTokens = new HashSet<>();

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
