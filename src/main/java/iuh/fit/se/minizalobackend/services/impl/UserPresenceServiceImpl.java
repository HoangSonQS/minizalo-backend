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
