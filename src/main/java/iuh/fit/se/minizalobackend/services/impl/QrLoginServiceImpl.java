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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrLoginServiceImpl implements QrLoginService {

    private static final long SESSION_TTL_MS = 180_000; // 3 minutes

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    private static class QrSession {
        String status;
        long createdAt;

        QrSession(String status, long createdAt) {
            this.status = status;
            this.createdAt = createdAt;
        }
    }

    private final ConcurrentHashMap<String, QrSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> generateSession() {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new QrSession("PENDING", System.currentTimeMillis()));
        Instant expiresAt = Instant.now().plusMillis(SESSION_TTL_MS);
        log.info("QR login session created: {}", sessionId);
        return Map.of("sessionId", sessionId, "expiresAt", expiresAt.toString());
    }

    @Override
    public SseEmitter subscribe(String sessionId) {
        QrSession session = sessions.get(sessionId);
        if (session == null || isExpired(session)) {
            return null;
        }

        SseEmitter emitter = new SseEmitter(SESSION_TTL_MS);

        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> {
            emitters.remove(sessionId);
            sessions.remove(sessionId);
        });
        emitter.onError(e -> emitters.remove(sessionId));

        emitters.put(sessionId, emitter);
        log.info("SSE subscriber connected for QR session: {}", sessionId);
        return emitter;
    }

    @Override
    public void confirmSession(String sessionId, String userId) {
        QrSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("QR session not found or expired");
        }
        if (isExpired(session)) {
            sessions.remove(sessionId);
            emitters.remove(sessionId);
            throw new IllegalArgumentException("QR session expired");
        }
        if (!"PENDING".equals(session.status)) {
            throw new IllegalArgumentException("QR session already used");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userId);
        userService.updateOnlineStatus(UUID.fromString(userId), true);

        session.status = "CONFIRMED";

        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("confirmed")
                        .data(Map.of(
                                "status", "CONFIRMED",
                                "accessToken", accessToken,
                                "refreshToken", refreshToken.getToken()
                        )));
                emitter.complete();
            } catch (IOException e) {
                log.warn("Failed to send SSE event for QR session {}: {}", sessionId, e.getMessage());
            }
        }

        sessions.remove(sessionId);
        log.info("QR login confirmed for user {} on session {}", userId, sessionId);
    }

    private boolean isExpired(QrSession session) {
        return System.currentTimeMillis() - session.createdAt > SESSION_TTL_MS;
    }

    @Scheduled(fixedRate = 60_000)
    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> {
            if (now - e.getValue().createdAt > SESSION_TTL_MS + 30_000) {
                SseEmitter emitter = emitters.remove(e.getKey());
                if (emitter != null) {
                    try {
                        emitter.send(SseEmitter.event().name("expired").data(Map.of("status", "EXPIRED")));
                        emitter.complete();
                    } catch (IOException ignored) {}
                }
                return true;
            }
            return false;
        });
    }
}
