package iuh.fit.se.minizalobackend.services.impl;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.MessagingErrorCode;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final UserRepository userRepository;

    @Override
    @Async
    @Transactional
    public void sendNotification(UUID userId, String token, String title, String body) {
        if (token == null || token.isEmpty()) {
            return;
        }

        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Message message = Message.builder()
                .setToken(token)
                .setNotification(notification)
                .build();

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Successfully sent message: {}", response);
        } catch (FirebaseMessagingException e) {
            log.error("Error sending FCM message: {}", e.getMessage());
            handleFcmException(userId, e);
        }
    }

    private void handleFcmException(UUID userId, FirebaseMessagingException e) {
        MessagingErrorCode errorCode = e.getMessagingErrorCode();
        if (errorCode == MessagingErrorCode.UNREGISTERED ||
                errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
            log.warn("Invalid/Unregistered token for user {}. Cleaning up...", userId);
            userRepository.findById(userId).ifPresent(user -> {
                user.setFcmToken(null);
                userRepository.save(user);
            });
        }
    }
}
