package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.dtos.response.websocket.UserPresenceMessage;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.UserPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserPresenceServiceImpl implements UserPresenceService {

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<UUID, Boolean> onlineUsers = new ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    @Transactional
    public void init() {
        log.info("Resetting all users to offline state...");
        try {
            userRepository.updateAllUsersOffline();
        } catch (Exception e) {
            log.error("Failed to reset user online status on startup", e);
        }
        onlineUsers.clear();
    }

    @Override
    @Transactional
    public void markUserOnline(UUID userId) {
        log.debug("User {} is online", userId);
        onlineUsers.put(userId, true);
        userRepository.findById(userId).ifPresent(user -> {
            user.setIsOnline(true);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);
            broadcastPresence(userId, true, user.getLastSeen());
        });
    }

    @Override
    @Transactional
    public void markUserOffline(UUID userId) {
        log.debug("User {} is offline", userId);
        onlineUsers.remove(userId);
        userRepository.findById(userId).ifPresent(user -> {
            user.setIsOnline(false);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);
            broadcastPresence(userId, false, user.getLastSeen());
        });
    }

    @Override
    public boolean isUserOnline(UUID userId) {
        return onlineUsers.getOrDefault(userId, false);
    }

    @Override
    @Transactional
    public void heartbeat(UUID userId) {
        // Update in-memory map
        onlineUsers.put(userId, true);

        // Update DB
        userRepository.findById(userId).ifPresent(user -> {
            LocalDateTime now = LocalDateTime.now();
            boolean statusChainged = !Boolean.TRUE.equals(user.getIsOnline());

            user.setLastSeen(now);
            if (statusChainged) {
                user.setIsOnline(true);
                log.debug("User {} presence changed to online via heartbeat", userId);
            }
            userRepository.save(user);

            // Broadcast if status changed or just to update lastSeen for listeners?
            // Usually we broadcast full status change. For heartbeat we might be chatty.
            // But lastSeen update is useful. Let's broadcast.
            broadcastPresence(userId, true, now);
        });
    }

    private void broadcastPresence(UUID userId, boolean isOnline, LocalDateTime lastSeen) {
        UserPresenceMessage message = UserPresenceMessage.builder()
                .userId(userId)
                .isOnline(isOnline)
                .lastSeen(lastSeen)
                .build();
        String destination = "/topic/presence/" + userId;
        messagingTemplate.convertAndSend(destination, message);
    }
}
