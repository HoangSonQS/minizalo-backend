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
        var user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalArgumentException("Error: User not found with ID: " + userId));
        // Bỏ tham chiếu refresh token cũ trên User (tránh xung đột quan hệ OneToOne)
        user.setRefreshToken(null);
        userRepository.saveAndFlush(user);

        // Xóa refresh token cũ theo user_id (native query) để chắc chắn không còn bản ghi trước khi INSERT
        refreshTokenRepository.deleteByUserId(user.getId());
        refreshTokenRepository.flush();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
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
