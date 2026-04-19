package iuh.fit.se.minizalobackend.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Expo/React Native đăng ký token dạng ExponentPushToken[…] — Firebase Admin không gửi được; dùng HTTP API của Expo.
     */
    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Async
    @Transactional
    public void sendNotification(UUID userId, String token, String title, String body, String roomId, String senderName) {
        if (token == null || token.isEmpty()) {
            return;
        }

        if (isExpoPushToken(token)) {
            sendViaExpoPush(userId, token, title, body, roomId, senderName);
            return;
        }

        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Message.Builder messageBuilder = Message.builder()
                .setToken(token)
                .setNotification(notification);

        if (roomId != null) {
            messageBuilder.putData("roomId", roomId);
        }
        if (senderName != null) {
            messageBuilder.putData("senderName", senderName);
        }

        Message message = messageBuilder.build();

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Successfully sent FCM message: {}", response);
        } catch (FirebaseMessagingException e) {
            log.error("Error sending FCM message: {}", e.getMessage());
            handleFcmException(userId, e);
        }
    }

    private static boolean isExpoPushToken(String token) {
        return token.startsWith("ExponentPushToken") || token.startsWith("ExpoPushToken");
    }

    private void sendViaExpoPush(UUID userId, String token, String title, String body, String roomId, String senderName) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("to", token);
            root.put("title", title);
            root.put("body", body);
            root.put("sound", "default");
            root.put("priority", "high");
            root.put("channelId", "default");

            ObjectNode data = objectMapper.createObjectNode();
            if (roomId != null) {
                data.put("roomId", roomId);
            }
            if (senderName != null) {
                data.put("senderName", senderName);
            }
            root.set("data", data);

            String json = objectMapper.writeValueAsString(root);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(EXPO_PUSH_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            log.debug("Expo push HTTP {} body {}", resp.statusCode(), resp.body());

            if (resp.statusCode() >= 400) {
                log.error("Expo push HTTP error status {}: {}", resp.statusCode(), resp.body());
                return;
            }

            JsonNode rootNode = objectMapper.readTree(resp.body());
            JsonNode dataNode = rootNode.get("data");
            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode item : dataNode) {
                    handleExpoTicket(userId, item);
                }
            } else if (dataNode != null) {
                handleExpoTicket(userId, dataNode);
            }
        } catch (Exception e) {
            log.error("Expo push failed: {}", e.getMessage());
        }
    }

    private void handleExpoTicket(UUID userId, JsonNode ticket) {
        if (ticket == null) {
            return;
        }
        String status = ticket.path("status").asText("");
        if (!"error".equalsIgnoreCase(status)) {
            return;
        }
        String message = ticket.path("message").asText("");
        if (message.contains("DeviceNotRegistered")
                || message.contains("InvalidCredentials")
                || message.contains("invalid")) {
            log.warn("Invalid Expo push token for user {} — clearing", userId);
            userRepository.findById(userId).ifPresent(user -> {
                user.setFcmToken(null);
                userRepository.save(user);
            });
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
