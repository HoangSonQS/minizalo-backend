package iuh.fit.se.minizalobackend.services;

import java.util.UUID;

public interface NotificationService {
    void sendNotification(UUID userId, String token, String title, String body, String roomId, String senderName, String type);
    void sendStoryNotification(UUID userId, String token, String title, String body, String storyOwnerId, String createdAt, String senderName);
}
