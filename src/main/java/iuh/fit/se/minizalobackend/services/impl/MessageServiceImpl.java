package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.models.RoomMember;
import iuh.fit.se.minizalobackend.repository.MessageDynamoRepository;
import iuh.fit.se.minizalobackend.repository.GroupRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.services.NotificationService;
import iuh.fit.se.minizalobackend.services.UserPresenceService;
import iuh.fit.se.minizalobackend.services.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
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

        // Trigger notifications for offline members
        triggerNotifications(message);

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
        log.debug("Fetching messages from DynamoDB for room: {}, limit: {}", roomId, limit);
        return messageDynamoRepository.getMessagesByRoomId(roomId.toString(), lastKey, limit);
    }

    @Override
    public void recallMessage(String messageId) {
        log.warn("recallMessage is not yet implemented for DynamoDB!");
    }
}