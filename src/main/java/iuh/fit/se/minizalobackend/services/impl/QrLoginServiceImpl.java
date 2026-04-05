package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.models.RefreshToken;
import iuh.fit.se.minizalobackend.security.JwtTokenProvider;
import iuh.fit.se.minizalobackend.services.QrLoginService;
import iuh.fit.se.minizalobackend.services.RefreshTokenService;
import iuh.fit.se.minizalobackend.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrLoginServiceImpl implements QrLoginService {

    private static final long SESSION_TTL_SECONDS = 180; // 3 minutes

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    private record QrSession(String status, String userId, String accessToken, String refreshToken, Instant createdAt) {}

    private final ConcurrentHashMap<String, QrSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> generateSession() {
        String sessionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        sessions.put(sessionId, new QrSession("PENDING", null, null, null, now));
        Instant expiresAt = now.plusSeconds(SESSION_TTL_SECONDS);
        log.info("QR login session created: {}", sessionId);
        return Map.of("sessionId", sessionId, "expiresAt", expiresAt.toString());
    }

    @Override
    public Map<String, Object> getSessionStatus(String sessionId) {
        QrSession session = sessions.get(sessionId);
        if (session == null) {
            return Map.of("status", "EXPIRED");
        }
        if (Instant.now().isAfter(session.createdAt().plusSeconds(SESSION_TTL_SECONDS))) {
            sessions.remove(sessionId);
            return Map.of("status", "EXPIRED");
        }
        if ("CONFIRMED".equals(session.status())) {
            sessions.remove(sessionId);
            return Map.of(
                "status", "CONFIRMED",
                "accessToken", session.accessToken(),
                "refreshToken", session.refreshToken()
            );
        }
        return Map.of("status", session.status());
    }

    @Override
    public void confirmSession(String sessionId, String userId) {
        QrSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("QR session not found or expired");
        }
        if (Instant.now().isAfter(session.createdAt().plusSeconds(SESSION_TTL_SECONDS))) {
            sessions.remove(sessionId);
            throw new IllegalArgumentException("QR session expired");
        }
        if (!"PENDING".equals(session.status())) {
            throw new IllegalArgumentException("QR session already used");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userId);
        userService.updateOnlineStatus(java.util.UUID.fromString(userId), true);

        sessions.put(sessionId, new QrSession("CONFIRMED", userId, accessToken, refreshToken.getToken(), session.createdAt()));
        log.info("QR login confirmed for user {} on session {}", userId, sessionId);
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minusSeconds(SESSION_TTL_SECONDS + 30);
        sessions.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
    }
}
