package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.models.RefreshToken;

import java.util.Optional;

public interface RefreshTokenService {
    Optional<RefreshToken> findByToken(String token);

    RefreshToken createRefreshToken(String userId, String deviceType, String deviceId, boolean revokeExistingSameType);
    // Backward-compatible signature for existing tests/callers
    default RefreshToken createRefreshToken(String userId) {
        return createRefreshToken(userId, "WEB", "unknown", false);
    }

    RefreshToken verifyExpiration(RefreshToken token);

    RefreshToken rotateRefreshToken(RefreshToken oldToken);

    void deleteByUserId(String userId);

    void deleteByToken(String token);
}
