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
    public RefreshToken createRefreshToken(String userId) {

        UUID userUUID = UUID.fromString(userId);
        
        // Delete existing refresh token for this user before creating new one
        userRepository.findById(userUUID).ifPresent(user -> {
            if (user.getRefreshToken() != null) {
                refreshTokenRepository.delete(user.getRefreshToken());
                user.setRefreshToken(null);
                userRepository.save(user);
                refreshTokenRepository.flush();
            }
        });
        

        // Delete existing refresh token for this user to avoid duplicate key violation
        userRepository.findById(UUID.fromString(userId)).ifPresent(user -> {
            refreshTokenRepository.deleteByUser(user);
            refreshTokenRepository.flush();
        });


        refreshToken.setUser(userRepository.findById(userUUID)
                .orElseThrow(() -> new IllegalArgumentException("Error: User not found with ID: " + userId)));
        refreshToken.setExpiryDate(Instant.now().plusSeconds(refreshTokenExpirationDays * 24 * 60 * 60));
        refreshToken.setToken(UUID.randomUUID().toString());

        refreshToken = refreshTokenRepository.save(refreshToken);
        return refreshToken;
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
        userRepository.findById(oldToken.getUser().getId()).ifPresent(user -> {
            user.setRefreshToken(null);
            userRepository.save(user);
        });

        refreshTokenRepository.deleteById(oldToken.getId());
        refreshTokenRepository.flush();
        return createRefreshToken(oldToken.getUser().getId().toString());
    }

    @Override
    @Transactional
    public void deleteByUserId(String userId) {
        userRepository.findById(UUID.fromString(userId)).ifPresent(user -> {
            refreshTokenRepository.deleteByUser(user);
            refreshTokenRepository.flush(); // Ensure deletion is committed
        });
    }
}
