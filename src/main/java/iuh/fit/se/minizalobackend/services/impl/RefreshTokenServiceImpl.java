package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.exception.TokenRefreshException;
import iuh.fit.se.minizalobackend.models.RefreshToken;
import iuh.fit.se.minizalobackend.repository.RefreshTokenRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.RefreshTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    @Value("${app.refresh.token.expiration.days}")
    private Long refreshTokenExpirationDays;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Override
    @Transactional
    public RefreshToken createRefreshToken(String userId, String deviceType, String deviceId, boolean revokeExistingSameType) {

        var user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalArgumentException("Error: User not found with ID: " + userId));

        final String normalizedType = (deviceType == null || deviceType.isBlank()) ? "WEB" : deviceType.trim().toUpperCase();
        final String normalizedDeviceId = (deviceId == null || deviceId.isBlank()) ? "unknown" : deviceId.trim();

        if (revokeExistingSameType) {
            refreshTokenRepository.deleteByUserIdAndDeviceType(user.getId(), normalizedType);
            refreshTokenRepository.flush();
        }

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(Instant.now().plusSeconds(refreshTokenExpirationDays * 24 * 60 * 60));
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setDeviceType(normalizedType);
        refreshToken.setDeviceId(normalizedDeviceId);

        refreshToken = refreshTokenRepository.save(refreshToken);
        return refreshToken;
    }

    // Backward-compatible overload for direct impl calls in existing tests
    public RefreshToken createRefreshToken(String userId) {
        return createRefreshToken(userId, "WEB", "unknown", false);
    }

    @Override
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException(token.getToken(),
                    "Refresh token was expired. Please make a new signin request");
        }

        return token;
    }

    @Override
    @Transactional
    public RefreshToken rotateRefreshToken(RefreshToken oldToken) {
        refreshTokenRepository.deleteById(oldToken.getId());
        refreshTokenRepository.flush();
        return createRefreshToken(
                oldToken.getUser().getId().toString(),
                oldToken.getDeviceType(),
                oldToken.getDeviceId(),
                false
        );
    }

    @Override
    @Transactional
    public void deleteByUserId(String userId) {
        userRepository.findById(UUID.fromString(userId)).ifPresent(user -> {
            refreshTokenRepository.deleteByUser(user);
            refreshTokenRepository.flush(); // Ensure deletion is committed
        });
    }

    @Override
    @Transactional
    public void deleteByToken(String token) {
        if (token == null || token.isBlank()) return;
        refreshTokenRepository.deleteByToken(token);
        refreshTokenRepository.flush();
    }
}
