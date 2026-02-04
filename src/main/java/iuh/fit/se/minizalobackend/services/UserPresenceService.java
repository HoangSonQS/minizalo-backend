package iuh.fit.se.minizalobackend.services;

import java.util.UUID;

public interface UserPresenceService {
    void markUserOnline(UUID userId);

    void markUserOffline(UUID userId);

    boolean isUserOnline(UUID userId);

    void heartbeat(UUID userId);
}
