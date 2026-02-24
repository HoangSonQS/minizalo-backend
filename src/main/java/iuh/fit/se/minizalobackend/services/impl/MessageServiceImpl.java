package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.dtos.response.SearchMessageResponse;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.payload.request.ChatMessageRequest;
import iuh.fit.se.minizalobackend.models.MessageReaction;
import iuh.fit.se.minizalobackend.models.RoomMember;
import iuh.fit.se.minizalobackend.repository.MessageDynamoRepository;
import iuh.fit.se.minizalobackend.repository.GroupRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.services.NotificationService;
import iuh.fit.se.minizalobackend.services.UserPresenceService;
import iuh.fit.se.minizalobackend.services.MessageService;
import iuh.fit.se.minizalobackend.services.AnalyticsService;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.utils.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageDynamoRepository messageDynamoRepository;
    private final GroupRepository groupRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserPresenceService userPresenceService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;

    @Override
    public MessageDynamo saveMessage(MessageDynamo message) {
        // Ensure required fields are set before saving
        if (message.getMessageId() == null) {
            message.setMessageId(UUID.randomUUID().toString());
        }
        if (message.getCreatedAt() == null) {
            message.setCreatedAt(Instant.now().toString());
        }
        log.debug("Saving message to DynamoDB for chat room: {}", message.getChatRoomId());
        messageDynamoRepository.save(message);

        // Log activity
        analyticsService.logActivity(UUID.fromString(message.getSenderId()), AppConstants.ACTIVITY_MESSAGE_SENT,
                "Message sent to room: " + message.getChatRoomId());

        // Trigger notifications for offline members
        triggerNotifications(message);

        return message;
    }

    @Override
    public MessageDynamo forwardMessage(String originalRoomId, String originalMessageId, String targetRoomId,
            String senderId) {
        MessageDynamo originalMessage = messageDynamoRepository.getMessage(originalRoomId, originalMessageId)
                .orElseThrow(() -> new IllegalArgumentException("Original message not found"));

        User sender = userRepository.findById(UUID.fromString(senderId))
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        MessageDynamo forwardedMessage = new MessageDynamo();
        forwardedMessage.setMessageId(UUID.randomUUID().toString());
        forwardedMessage.setChatRoomId(targetRoomId);
        forwardedMessage.setSenderId(senderId);
        forwardedMessage
                .setSenderName(sender.getDisplayName() != null ? sender.getDisplayName() : sender.getUsername());
        forwardedMessage.setContent(originalMessage.getContent());
        forwardedMessage.setType(originalMessage.getType());
        forwardedMessage.setAttachments(originalMessage.getAttachments());
        forwardedMessage.setCreatedAt(Instant.now().toString());
        forwardedMessage.setRead(false);
        forwardedMessage.setReadBy(new ArrayList<>());
        forwardedMessage.setReactions(new ArrayList<>());
        forwardedMessage.setRecalled(false);
        forwardedMessage.setPinned(false);

        messageDynamoRepository.save(forwardedMessage);

        // Broad-cast to target room
        String destination = "/topic/chat/" + targetRoomId;
        messagingTemplate.convertAndSend(destination, forwardedMessage);

        // Log activity
        analyticsService.logActivity(UUID.fromString(senderId), AppConstants.ACTIVITY_MESSAGE_FORWARDED,
                "Forwarded message " + originalMessageId + " to room " + targetRoomId);

        return forwardedMessage;
    }

    @Override
    public MessageDynamo processMessage(ChatMessageRequest request, String senderId) {
        User sender = userRepository.findById(UUID.fromString(senderId))
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        MessageDynamo message = new MessageDynamo();
        message.setMessageId(UUID.randomUUID().toString());
        message.setChatRoomId(request.getReceiverId()); // In this app roomId = receiverId for 1:1 or groupId
        message.setSenderId(senderId);
        message.setSenderName(sender.getDisplayName() != null ? sender.getDisplayName() : sender.getUsername());
        message.setContent(request.getContent());

        message.setAttachments(request.getAttachments());
        message.setType(determineMessageType(request));
        message.setCreatedAt(Instant.now().toString());
        message.setReplyToMessageId(request.getReplyToMessageId());
        message.setRead(false);
        message.setReadBy(new ArrayList<>());
        message.setReactions(new ArrayList<>());

        saveMessage(message);

        // Broadcast to room
        String destination = "/topic/chat/" + message.getChatRoomId();
        messagingTemplate.convertAndSend(destination, message);

        return message;
    }

    private void triggerNotifications(MessageDynamo message) {
        try {
            UUID roomId = UUID.fromString(message.getChatRoomId());
            UUID senderId = UUID.fromString(message.getSenderId());

            groupRepository.findById(roomId).ifPresent(room -> {
                List<RoomMember> members = roomMemberRepository.findAllByRoom(room);
                for (RoomMember member : members) {
                    UUID recipientId = member.getUser().getId();
                    // Don't notify the sender
                    if (!recipientId.equals(senderId)) {
                        if (!userPresenceService.isUserOnline(recipientId)) {
                            String fcmToken = member.getUser().getFcmToken();
                            if (fcmToken != null && !fcmToken.isEmpty()) {
                                log.debug("Sending push notification to offline user: {}", recipientId);
                                notificationService.sendNotification(
                                        recipientId,
                                        fcmToken,
                                        "New Message",
                                        "You have a new message from " + message.getSenderName());
                            }
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to trigger notifications: {}", e.getMessage());
        }
    }

    @Override
    public PaginatedMessageResult getRoomMessages(UUID roomId, String lastKey, int limit) {
        log.info("Fetching messages from DynamoDB for room: {}, limit: {}", roomId, limit);
        PaginatedMessageResult result = messageDynamoRepository.getMessagesByRoomId(roomId.toString(), lastKey, limit);
        log.info("Found {} messages for room {}", result.getMessages().size(), roomId);
        return result;
    }

    @Override
    public void recallMessage(String chatRoomId, String messageId) {
        messageDynamoRepository.getMessage(chatRoomId, messageId).ifPresent(message -> {
            Instant createdAt = Instant.parse(message.getCreatedAt());
            Instant now = Instant.now();

            // Allow recall only within 24 hours
            if (now.isBefore(createdAt.plus(24, java.time.temporal.ChronoUnit.HOURS))) {
                message.setRecalled(true);
                message.setRecalledAt(now.toString());
                messageDynamoRepository.save(message);

                // Broadcast recall event
                String destination = "/topic/chat/" + chatRoomId + "/recall";
                messagingTemplate.convertAndSend(destination, Map.of(
                        "messageId", messageId,
                        "recalledAt", message.getRecalledAt()));

                log.info("Message {} recalled in room {}", messageId, chatRoomId);
            } else {
                log.warn("Recall failed: Message {} is older than 24 hours", messageId);
                throw new IllegalArgumentException("Cannot recall message after 24 hours");
            }
        });
    }

    @Override
    public void markMessageAsRead(String chatRoomId, String messageId, String userId) {
        messageDynamoRepository.getMessage(chatRoomId, messageId).ifPresent(message -> {
            if (message.getReadBy() == null) {
                message.setReadBy(new ArrayList<>());
            }
            if (!message.getReadBy().contains(userId)) {
                message.getReadBy().add(userId);
                message.setRead(true);
                messageDynamoRepository.save(message);

                // Broadcast read receipt
                String destination = "/topic/chat/" + chatRoomId + "/read";
                messagingTemplate.convertAndSend(destination, Map.of(
                        "messageId", messageId,
                        "userId", userId,
                        "readAt", Instant.now().toString()));
            }
        });
    }

    @Override
    public void addReaction(String chatRoomId, String messageId, String userId, String emoji) {
        messageDynamoRepository.getMessage(chatRoomId, messageId).ifPresent(message -> {
            if (message.getReactions() == null) {
                message.setReactions(new ArrayList<>());
            }
            // Remove existing reaction from this user if any
            message.getReactions().removeIf(r -> r.getUserId().equals(userId));

            message.getReactions().add(MessageReaction.builder()
                    .userId(userId)
                    .emoji(emoji)
                    .build());

            messageDynamoRepository.save(message);

            // Broadcast reaction
            String destination = "/topic/chat/" + chatRoomId + "/reaction";
            messagingTemplate.convertAndSend(destination, Map.of(
                    "messageId", messageId,
                    "userId", userId,
                    "emoji", emoji));
        });
    }

    @Override
    public void pinMessage(String chatRoomId, String messageId, boolean pin) {
        messageDynamoRepository.getMessage(chatRoomId, messageId).ifPresent(message -> {
            message.setPinned(pin);
            messageDynamoRepository.save(message);

            // Broadcast pin event
            String destination = "/topic/chat/" + chatRoomId + "/pin";
            messagingTemplate.convertAndSend(destination, Map.of(
                    "messageId", messageId,
                    "isPinned", pin));

            log.info("Message {} {} in room {}", messageId, pin ? "pinned" : "unpinned", chatRoomId);
        });
    }

    private String determineMessageType(ChatMessageRequest request) {
        if (request.getAttachments() == null || request.getAttachments().isEmpty()) {
            return AppConstants.MESSAGE_TYPE_TEXT;
        }
        String mimeType = request.getAttachments().get(0).getType();
        if (mimeType == null)
            return AppConstants.MESSAGE_TYPE_FILE;

        String lowerMime = mimeType.toLowerCase();
        if (lowerMime.startsWith("image"))
            return AppConstants.MESSAGE_TYPE_IMAGE;
        if (lowerMime.startsWith("video"))
            return AppConstants.MESSAGE_TYPE_VIDEO;
        return AppConstants.MESSAGE_TYPE_DOCUMENT;
    }

    @Override
    public SearchMessageResponse searchMessages(UUID roomId, String query, int limit, String lastKey) {
        return messageDynamoRepository.searchMessages(roomId.toString(), query, limit, lastKey);
    }
}