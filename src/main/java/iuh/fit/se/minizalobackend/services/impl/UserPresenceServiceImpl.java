package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.services.UserPresenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class UserPresenceServiceImpl implements UserPresenceService {

    private final Map<UUID, Boolean> onlineUsers = new ConcurrentHashMap<>();

    @Override
    public void markUserOnline(UUID userId) {
        log.debug("User {} is online", userId);
        onlineUsers.put(userId, true);
    }

    @Override
    public void markUserOffline(UUID userId) {
        log.debug("User {} is offline", userId);
        onlineUsers.remove(userId);
    }

    @Override
    public boolean isUserOnline(UUID userId) {
        return onlineUsers.getOrDefault(userId, false);
    }
}
