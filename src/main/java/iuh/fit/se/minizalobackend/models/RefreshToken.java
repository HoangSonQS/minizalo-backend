package iuh.fit.se.minizalobackend.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private Instant expiryDate;

    /**
     * Device type for session limiting (WEB vs MOBILE).
     * Stored as string for simplicity.
     */
    @Column(nullable = false)
    private String deviceType;

    /**
     * Per-install / per-browser-profile identifier.
     * - Web: persisted in localStorage (same across tabs in same browser profile)
     * - Mobile: persisted in AsyncStorage (same for that app install)
     */
    @Column(nullable = false)
    private String deviceId;
}
