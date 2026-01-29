package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.models.RefreshToken;

import java.util.Optional;

public interface RefreshTokenService {
    Optional<RefreshToken> findByToken(String token);

    RefreshToken createRefreshToken(String userId);

    RefreshToken verifyExpiration(RefreshToken token);

    RefreshToken rotateRefreshToken(RefreshToken oldToken);

    void deleteByUserId(String userId);
}
