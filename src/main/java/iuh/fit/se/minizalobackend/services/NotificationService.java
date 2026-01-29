package iuh.fit.se.minizalobackend.services;

import java.util.UUID;

public interface NotificationService {
    void sendNotification(UUID userId, String token, String title, String body);
}
