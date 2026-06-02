package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.dtos.response.SearchMessageResponse;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.payload.request.ChatMessageRequest;
import iuh.fit.se.minizalobackend.models.EFriendStatus;
import iuh.fit.se.minizalobackend.models.EPrivacyAudience;
import iuh.fit.se.minizalobackend.models.Friend;
import iuh.fit.se.minizalobackend.models.MessageReaction;
import iuh.fit.se.minizalobackend.models.RoomMember;
import iuh.fit.se.minizalobackend.models.ChatRoom;
import iuh.fit.se.minizalobackend.models.ERoomType;
import iuh.fit.se.minizalobackend.repository.ChatRoomRepository;
import iuh.fit.se.minizalobackend.repository.FriendRepository;
import iuh.fit.se.minizalobackend.repository.MessageDynamoRepository;
import iuh.fit.se.minizalobackend.repository.GroupSettingsRepository;
import iuh.fit.se.minizalobackend.repository.GroupRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.services.NotificationService;
import iuh.fit.se.minizalobackend.services.MessageService;
import iuh.fit.se.minizalobackend.services.AnalyticsService;
import iuh.fit.se.minizalobackend.services.ModerationService;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.utils.AppConstants;
import iuh.fit.se.minizalobackend.models.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageDynamoRepository messageDynamoRepository;
    private final GroupRepository groupRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final FriendRepository friendRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final GroupSettingsRepository groupSettingsRepository;
    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;
    private final iuh.fit.se.minizalobackend.services.MinioService minioService;
    private final ModerationService moderationService;

    @Override
    public void deleteAllMessages(String chatRoomId) {
        log.info("Deleting all messages for room: {}", chatRoomId);
        messageDynamoRepository.deleteAllByRoomId(chatRoomId);
    }

    @Override
    public void deleteCloudMessage(String chatRoomId, String messageId, String requesterId) {
        ChatRoom room = chatRoomRepository.findById(UUID.fromString(chatRoomId))
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        if (room.getType() != ERoomType.CLOUD) {
            throw new IllegalArgumentException("Only cloud messages can be deleted here");
        }
        if (requesterId == null || !roomMemberRepository.existsByRoom_IdAndUser_Id(room.getId(), UUID.fromString(requesterId))) {
            throw new IllegalArgumentException("Not allowed to delete this cloud message");
        }
        MessageDynamo message = messageDynamoRepository.getMessage(chatRoomId, messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (requesterId != null && message.getSenderId() != null && !requesterId.equals(message.getSenderId())) {
            throw new IllegalArgumentException("Only sender can delete this cloud message");
        }
        boolean deleted = messageDynamoRepository.deleteByMessageId(chatRoomId, messageId);
        if (!deleted) {
            throw new IllegalArgumentException("Message not found");
        }
        messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId + "/recall", Map.of(
                "messageId", messageId,
                "deleted", true,
                "recalledAt", Instant.now().toString()));
        log.info("Cloud message {} deleted in room {}", messageId, chatRoomId);
    }

    @Override
    public void deleteCloudMediaItems(String chatRoomId, List<Map<String, String>> items, String requesterId) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("No media selected");
        }
        ChatRoom room = chatRoomRepository.findById(UUID.fromString(chatRoomId))
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        if (room.getType() != ERoomType.CLOUD) {
            throw new IllegalArgumentException("Only cloud media can be deleted here");
        }
        if (requesterId == null || !roomMemberRepository.existsByRoom_IdAndUser_Id(room.getId(), UUID.fromString(requesterId))) {
            throw new IllegalArgumentException("Not allowed to delete this cloud media");
        }

        Set<String> normalizedUrls = new HashSet<>();
        Set<String> selectedMessageIds = new HashSet<>();
        for (Map<String, String> item : items) {
            if (item == null) continue;
            String url = item.get("url");
            if (url != null && !url.isBlank()) {
                normalizedUrls.add(normalizeUrlForCompare(url));
            }
            String messageId = item.get("messageId");
            if (messageId != null && !messageId.isBlank()) {
                selectedMessageIds.add(messageId);
            }
        }
        if (normalizedUrls.isEmpty()) {
            throw new IllegalArgumentException("No media selected");
        }

        List<MessageDynamo> messages = new ArrayList<>();
        for (String messageId : selectedMessageIds) {
            messageDynamoRepository.getMessage(chatRoomId, messageId).ifPresent(messages::add);
        }
        if (messages.isEmpty()) {
            PaginatedMessageResult result = messageDynamoRepository.getMessagesByRoomId(chatRoomId, null, 1000);
            messages.addAll(result.getMessages());
        }

        int deletedCount = 0;
        for (MessageDynamo message : messages) {
            if (message.getSenderId() != null && !requesterId.equals(message.getSenderId())) {
                continue;
            }
            if (message.getAttachments() == null || message.getAttachments().isEmpty()) {
                if (message.getContent() != null && urlMatchesAny(message.getContent().trim(), normalizedUrls)) {
                    if (messageDynamoRepository.deleteByMessageId(chatRoomId, message.getMessageId())) {
                        deletedCount++;
                        broadcastCloudMediaDelete(chatRoomId, message.getMessageId());
                    }
                }
                continue;
            }

            int before = message.getAttachments().size();
            message.setAttachments(message.getAttachments().stream()
                    .filter(att -> att == null || att.getUrl() == null || !urlMatchesAny(att.getUrl(), normalizedUrls))
                    .toList());
            int removed = before - message.getAttachments().size();
            if (removed <= 0) {
                continue;
            }
            deletedCount += removed;
            if (message.getAttachments().isEmpty()) {
                messageDynamoRepository.deleteByMessageId(chatRoomId, message.getMessageId());
                broadcastCloudMediaDelete(chatRoomId, message.getMessageId());
            } else {
                messageDynamoRepository.save(message);
                broadcastMessageUpdate(chatRoomId, message);
            }
        }

        if (deletedCount == 0) {
            throw new IllegalArgumentException("No matching media found");
        }
        log.info("Deleted {} cloud media item(s) in room {}", deletedCount, chatRoomId);
    }

    private String normalizeUrlForCompare(String url) {
        String trimmed = url == null ? "" : url.trim();
        int queryIndex = trimmed.indexOf('?');
        String noQuery = queryIndex >= 0 ? trimmed.substring(0, queryIndex) : trimmed;
        try {
            return java.net.URLDecoder.decode(noQuery, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return noQuery;
        }
    }

    private boolean urlMatchesAny(String url, Set<String> normalizedUrls) {
        String normalized = normalizeUrlForCompare(url);
        for (String selected : normalizedUrls) {
            if (normalized.equals(selected) || normalized.endsWith(selected) || selected.endsWith(normalized)) {
                return true;
            }
            String normalizedFile = normalized.substring(normalized.lastIndexOf('/') + 1);
            String selectedFile = selected.substring(selected.lastIndexOf('/') + 1);
            if (!normalizedFile.isBlank() && normalizedFile.equals(selectedFile)) {
                return true;
            }
        }
        return false;
    }

    private void broadcastCloudMediaDelete(String chatRoomId, String messageId) {
        messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId + "/recall", Map.of(
                "messageId", messageId,
                "deleted", true,
                "recalledAt", Instant.now().toString()));
    }

    private void broadcastMessageUpdate(String chatRoomId, MessageDynamo message) {
        try {
            MessageDynamo normalized = normalizeMessage(message);
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("messageUpdate", true);
            payload.put("messageId", normalized.getMessageId());
            payload.put("chatRoomId", normalized.getChatRoomId());
            payload.put("senderId", normalized.getSenderId());
            payload.put("senderName", normalized.getSenderName());
            payload.put("content", normalized.getContent());
            payload.put("type", normalized.getType());
            payload.put("createdAt", normalized.getCreatedAt());
            payload.put("attachments", normalized.getAttachments());
            payload.put("recalled", normalized.isRecalled());
            payload.put("pinned", normalized.isPinned());
            payload.put("reactions", normalized.getReactions());
            payload.put("readBy", normalized.getReadBy());
            payload.put("replyToMessageId", normalized.getReplyToMessageId());
            messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId, payload);
        } catch (Exception e) {
            log.warn("[broadcastMessageUpdate] failed: {}", e.getMessage());
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
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

        // Async AI Moderation for GROUP and CLOUD rooms
        if (message.getType() != null && "TEXT".equalsIgnoreCase(message.getType())) {
            chatRoomRepository.findById(UUID.fromString(message.getChatRoomId())).ifPresent(room -> {
                if (room.getType() == ERoomType.GROUP || room.getType() == ERoomType.CLOUD) {
                    moderationService.scanAndFlagMessage(message.getMessageId(), message.getChatRoomId(), message.getSenderId(), message.getContent());
                }
            });
        }

        // Log activity (skip for SYSTEM messages)
        if (!"SYSTEM".equals(message.getSenderId())) {
            analyticsService.logActivity(UUID.fromString(message.getSenderId()), AppConstants.ACTIVITY_MESSAGE_SENT,
                    "Message sent to room: " + message.getChatRoomId());
            // Broadcast live event to admin dashboard
            try {
                messagingTemplate.convertAndSend("/topic/admin/live", java.util.Map.of("type", "MESSAGE_SENT"));
            } catch (Exception e) {
                log.warn("Failed to broadcast to /topic/admin/live: {}", e.getMessage());
            }
        }

        // Trigger notifications for offline members (skip for SYSTEM/privacy-blocked
        // messages)
        if (!"SYSTEM".equals(message.getSenderId()) && !message.isPrivacyBlocked()) {
            triggerNotifications(message);
        }

        return message;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public MessageDynamo forwardMessage(String originalRoomId, String originalMessageId, String targetRoomId,
            String senderId) {
        MessageDynamo originalMessage = messageDynamoRepository.getMessage(originalRoomId, originalMessageId)
                .orElseThrow(() -> new IllegalArgumentException("Original message not found"));

        User sender = userRepository.findById(UUID.fromString(senderId))
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        UUID targetUuid = UUID.fromString(targetRoomId);
        Optional<ChatRoom> targetRoomOpt = chatRoomRepository.findById(targetUuid);
        if (targetRoomOpt.isPresent()) {
            if (targetRoomOpt.get().getType() == ERoomType.DIRECT) {
                List<RoomMember> tMembers = roomMemberRepository.findAllByRoomWithUsersFetched(targetRoomOpt.get());
                Optional<User> otherOpt = tMembers.stream()
                        .map(RoomMember::getUser)
                        .filter(u -> !u.getId().toString().equals(senderId))
                        .findFirst();
                if (otherOpt.isPresent()) {
                    User recipient = userRepository.findById(otherOpt.get().getId()).orElse(otherOpt.get());
                    assertRecipientAcceptsDirectMessageFrom(sender, recipient);
                }
            } else if (targetRoomOpt.get().getType() == ERoomType.GROUP) {
                boolean isMember = roomMemberRepository.existsByRoom_IdAndUser_Id(targetUuid, sender.getId());
                if (!isMember) {
                    throw new IllegalStateException("YOU_ARE_NOT_A_MEMBER_OF_THIS_GROUP");
                }
            }
        }

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
        messagingTemplate.convertAndSend(destination, normalizeMessage(forwardedMessage));

        // Log activity
        analyticsService.logActivity(UUID.fromString(senderId), AppConstants.ACTIVITY_MESSAGE_FORWARDED,
                "Forwarded message " + originalMessageId + " to room " + targetRoomId);

        // Trigger notifications
        triggerNotifications(forwardedMessage);

        return forwardedMessage;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public MessageDynamo processMessage(ChatMessageRequest request, String senderId) {
        User sender = userRepository.findById(UUID.fromString(senderId))
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        // DIRECT: chặn 2 chiều vẫn throw; riêng policy người lạ thì lưu tin phía người
        // gửi + đánh dấu privacyBlocked.
        boolean strangerPrivacyBlocked = false;
        try {
            enforceDirectChatOutgoingRules(sender, request.getReceiverId());
        } catch (IllegalStateException ex) {
            if (AppConstants.STRANGER_MESSAGES_NOT_ALLOWED.equals(ex.getMessage())) {
                strangerPrivacyBlocked = true;
            } else {
                throw ex;
            }
        }

        UUID receiverUuid;
        try {
            receiverUuid = UUID.fromString(request.getReceiverId());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid receiver id");
        }
        Optional<ChatRoom> roomOpt = chatRoomRepository.findById(receiverUuid);
        if (roomOpt.isPresent() && roomOpt.get().getType() == ERoomType.GROUP) {
            boolean isMember = roomMemberRepository.existsByRoom_IdAndUser_Id(receiverUuid, sender.getId());
            if (!isMember) {
                throw new IllegalStateException("YOU_ARE_NOT_A_MEMBER_OF_THIS_GROUP");
            }
        }

        if (request.getReplyToMessageId() != null && !request.getReplyToMessageId().isBlank()) {
            boolean exists = messageDynamoRepository
                    .getMessage(request.getReceiverId(), request.getReplyToMessageId())
                    .isPresent();
            if (!exists) {
                throw new IllegalArgumentException("Reply target message not found in this room");
            }
        }

        MessageDynamo message = new MessageDynamo();
        message.setMessageId(UUID.randomUUID().toString());
        message.setChatRoomId(request.getReceiverId()); // In this app roomId = receiverId for 1:1 or groupId
        message.setSenderId(senderId);
        message.setSenderName(sender.getDisplayName() != null ? sender.getDisplayName() : sender.getUsername());
        message.setContent(request.getContent());

        message.setAttachments(request.getAttachments());
        message.setType(resolveMessageType(request));
        message.setCreatedAt(Instant.now().toString());
        message.setReplyToMessageId(request.getReplyToMessageId());
        message.setRead(false);
        message.setReadBy(new ArrayList<>());
        message.setReactions(new ArrayList<>());
        message.setPrivacyBlocked(strangerPrivacyBlocked);

        log.info("[DEBUG] ProcessMessage attachments count: {}",
                message.getAttachments() != null ? message.getAttachments().size() : "null");

        if (roomOpt.isPresent()) {
            List<RoomMember> members = roomMemberRepository.findAllByRoom(roomOpt.get());
            for (RoomMember m : members) {
                if (m.getChatDeletedAt() != null) {
                    boolean wasHidden = false;
                    try {
                        PaginatedMessageResult lastMsgResult = messageDynamoRepository.getMessagesByRoomId(
                                roomOpt.get().getId().toString(), null, 1);
                        if (lastMsgResult != null && lastMsgResult.getMessages() != null && !lastMsgResult.getMessages().isEmpty()) {
                            MessageDynamo lastMsg = lastMsgResult.getMessages().get(0);
                            if (!Instant.parse(lastMsg.getCreatedAt()).isAfter(m.getChatDeletedAt())) {
                                wasHidden = true;
                            }
                        } else {
                            wasHidden = true;
                        }
                    } catch (Exception ex) {
                        wasHidden = true;
                    }

                    if (wasHidden) {
                        try {
                            messagingTemplate.convertAndSendToUser(
                                    m.getUser().getId().toString(),
                                    "/queue/rooms",
                                    Map.of("action", "ADDED", "roomId", roomOpt.get().getId().toString())
                            );
                        } catch (Exception ex) {
                            log.warn("Could not notify room ADDED: {}", ex.getMessage());
                        }
                    }
                }
            }
        }

        saveMessage(message);
        if (strangerPrivacyBlocked) {
            String senderDest = "/topic/chat/" + message.getChatRoomId() + "/" + senderId;
            messagingTemplate.convertAndSend(senderDest, normalizeMessage(message));
        } else {
            String destination = "/topic/chat/" + message.getChatRoomId();
            messagingTemplate.convertAndSend(destination, normalizeMessage(message));
        }

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
                        // 1. Gửi tín hiệu WebSocket để cập nhật Badge/Danh sách chat tức thì
                        try {
                            String userDest = "/queue/rooms";
                            messagingTemplate.convertAndSendToUser(
                                recipientId.toString(), 
                                userDest, 
                                Map.of("action", "UNREAD_UPDATE", "roomId", message.getChatRoomId())
                            );
                        } catch (Exception e) {
                            log.warn("Failed to send WebSocket unread update to user {}: {}", recipientId, e.getMessage());
                        }

                        // 2. Luôn gửi push khi có token
                        String fcmToken = member.getUser().getFcmToken();
                        if (fcmToken != null && !fcmToken.isEmpty()) {
                            log.debug("Sending push notification to user: {}", recipientId);
                            notificationService.sendNotification(
                                    recipientId,
                                    fcmToken,
                                    "New Message",
                                    "You have a new message from " + message.getSenderName(),
                                    message.getChatRoomId(),
                                    message.getSenderName(),
                                    "MESSAGE");
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

        // Normalize URLs for all messages
        if (result.getMessages() != null) {
            result.getMessages().forEach(this::normalizeMessage);
        }

        log.info("Found {} messages for room {}", result.getMessages().size(), roomId);
        return result;
    }

    @Override
    public MessageDynamo updateMessageContent(String chatRoomId, String messageId, String newContent, String newType) {
        Optional<MessageDynamo> opt = messageDynamoRepository.getMessage(chatRoomId, messageId);
        if (opt.isEmpty()) {
            log.warn("[updateMessageContent] message not found room={} id={}", chatRoomId, messageId);
            return null;
        }
        MessageDynamo message = opt.get();
        if (newContent != null) message.setContent(newContent);
        if (newType != null && !newType.isBlank()) message.setType(newType);
        messageDynamoRepository.save(message);

        // Broadcast cùng topic như tin bình thường nhưng kèm flag `messageUpdate=true`
        // để FE phân biệt: gọi updateMessage() thay vì addMessage().
        // (Không dùng topic phụ /updated để giảm số subscription cần maintain.)
        try {
            MessageDynamo normalized = normalizeMessage(message);
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("messageUpdate", true);
            payload.put("messageId", normalized.getMessageId());
            payload.put("chatRoomId", normalized.getChatRoomId());
            payload.put("senderId", normalized.getSenderId());
            payload.put("senderName", normalized.getSenderName());
            payload.put("content", normalized.getContent());
            payload.put("type", normalized.getType());
            payload.put("createdAt", normalized.getCreatedAt());
            payload.put("attachments", normalized.getAttachments());
            payload.put("recalled", normalized.isRecalled());
            payload.put("pinned", normalized.isPinned());
            payload.put("reactions", normalized.getReactions());
            payload.put("readBy", normalized.getReadBy());
            payload.put("replyToMessageId", normalized.getReplyToMessageId());
            String destination = "/topic/chat/" + chatRoomId;
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.warn("[updateMessageContent] broadcast failed: {}", e.getMessage());
        }
        return message;
    }

    @Override
    public void recallMessage(String chatRoomId, String messageId) {
        recallMessage(chatRoomId, messageId, null);
    }

    @Override
    public void recallMessage(String chatRoomId, String messageId, String requesterId) {
        messageDynamoRepository.getMessage(chatRoomId, messageId).ifPresent(message -> {
            if (requesterId != null && message.getSenderId() != null && !requesterId.equals(message.getSenderId())) {
                throw new IllegalArgumentException("Only sender can recall this message");
            }
            Instant createdAt = Instant.parse(message.getCreatedAt());
            Instant now = Instant.now();

            // Allow recall only within 1 day
            if (now.isBefore(createdAt.plus(1, java.time.temporal.ChronoUnit.DAYS))) {
                message.setRecalled(true);
                message.setRecalledAt(now.toString());
                messageDynamoRepository.save(message);

                // Broadcast recall event
                String destination = "/topic/chat/" + chatRoomId + "/recall";
                messagingTemplate.convertAndSend(destination, Map.of(
                        "messageId", messageId,
                        "recalledAt", message.getRecalledAt()));

                // Notify all members to refresh their room list (for last message preview update)
                try {
                    chatRoomRepository.findById(UUID.fromString(chatRoomId)).ifPresent(room -> {
                        roomMemberRepository.findAllByRoom(room).forEach(m -> {
                            messagingTemplate.convertAndSendToUser(
                                m.getUser().getId().toString(),
                                "/queue/rooms",
                                Map.of("action", "UNREAD_UPDATE", "roomId", chatRoomId)
                            );
                        });
                    });
                } catch (Exception e) {
                    log.warn("Failed to broadcast recall update to room members: {}", e.getMessage());
                }

                log.info("Message {} recalled in room {}", messageId, chatRoomId);
            } else {
                log.warn("Recall failed: Message {} is older than 1 day", messageId);
                throw new IllegalArgumentException("Cannot recall message after 1 day");
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

                // Cập nhật lastReadAt vào RoomMember (PostgreSQL) để tính unreadCount và phục vụ getOldestUnreadMessage
                try {
                    java.util.UUID rId = java.util.UUID.fromString(chatRoomId);
                    java.util.UUID uId = java.util.UUID.fromString(userId);
                    final String msgCreatedAt = message.getCreatedAt();
                    chatRoomRepository.findById(rId).ifPresent(room -> {
                        userRepository.findById(uId).ifPresent(user -> {
                            roomMemberRepository.findByRoomAndUser(room, user).ifPresent(member -> {
                                // Chuyển đổi sang LocalDateTime (UTC) để lưu vào Postgres
                                try {
                                    java.time.OffsetDateTime msgODT = java.time.OffsetDateTime.parse(msgCreatedAt);
                                    java.time.LocalDateTime msgTime = msgODT
                                            .atZoneSameInstant(java.time.ZoneOffset.UTC)
                                            .toLocalDateTime();
                                    
                                    // Chỉ cập nhật nếu tin nhắn này MỚI HƠN thời điểm đọc hiện tại
                                    if (member.getLastReadAt() == null || msgTime.isAfter(member.getLastReadAt())) {
                                        member.setLastReadAt(msgTime);
                                        roomMemberRepository.save(member);
                                        log.info("Updated RoomMember lastReadAt forward to {} for user {} in room {}", msgTime, userId, chatRoomId);
                                        
                                        // Phát tín hiệu đồng bộ Badge tới các thiết bị khác của CHÍNH NGƯỜI ĐỌC
                                        messagingTemplate.convertAndSendToUser(
                                            userId, 
                                            "/queue/rooms", 
                                            Map.of("action", "UNREAD_UPDATE", "roomId", chatRoomId)
                                        );
                                    } else {
                                        log.debug("Ignored stale read receipt (msgTime {} <= lastReadAt {}) for user {} in room {}", msgTime, member.getLastReadAt(), userId, chatRoomId);
                                    }
                                } catch (Exception parseEx) {
                                    // Fallback to now if parse fails
                                    member.setLastReadAt(java.time.LocalDateTime.now());
                                    roomMemberRepository.save(member);
                                }
                            });
                        });
                    });
                } catch (Exception e) {
                    log.error("Failed to update RoomMember lastReadAt: {}", e.getMessage());
                }
            }
        });
    }

    @Override
    public void addReaction(String chatRoomId, String messageId, String userId, String emoji) {
        messageDynamoRepository.getMessage(chatRoomId, messageId).ifPresent(message -> {
            if (message.getReactions() == null) {
                message.setReactions(new ArrayList<>());
            }
            // Luôn thêm reaction mới (cho phép nhiều reaction cùng loại từ cùng user)
            message.getReactions().add(MessageReaction.builder()
                    .userId(userId)
                    .emoji(emoji)
                    .build());

            messageDynamoRepository.save(message);

            String destination = "/topic/chat/" + chatRoomId + "/reaction";
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("messageId", messageId);
            payload.put("userId", userId);
            payload.put("emoji", emoji);
            payload.put("action", "add");
            messagingTemplate.convertAndSend(destination, payload);
        });
    }

    @Override
    public void removeReaction(String chatRoomId, String messageId, String userId) {
        messageDynamoRepository.getMessage(chatRoomId, messageId).ifPresent(message -> {
            if (message.getReactions() == null) {
                message.setReactions(new ArrayList<>());
            }
            boolean changed = message.getReactions().removeIf(r -> r.getUserId().equals(userId));
            if (!changed) {
                return;
            }

            messageDynamoRepository.save(message);

            String destination = "/topic/chat/" + chatRoomId + "/reaction";
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("messageId", messageId);
            payload.put("userId", userId);
            payload.put("emoji", null);
            payload.put("action", "removeAll");
            messagingTemplate.convertAndSend(destination, payload);
        });
    }

    @Override
    public void pinMessage(String chatRoomId, String messageId, boolean pin) {
        pinMessage(chatRoomId, messageId, pin, null, null);
    }

    @Override
    public void pinMessage(String chatRoomId, String messageId, boolean pin, String actorName, String messageType) {
        pinMessage(chatRoomId, messageId, pin, null, actorName, messageType);
    }

    @Override
    public void pinMessage(String chatRoomId, String messageId, boolean pin, String actorId, String actorName, String messageType) {
        messageDynamoRepository.getMessage(chatRoomId, messageId).ifPresent(message -> {
            if (pin && !message.isPinned()) {
                long pinnedCount = messageDynamoRepository.countPinnedMessages(chatRoomId);
                if (pinnedCount >= 5) {
                    throw new IllegalStateException("Chỉ được pin tối đa 5 tin nhắn trong một cuộc trò chuyện.");
                }
            }

            chatRoomRepository.findById(UUID.fromString(chatRoomId)).ifPresent(room -> {
                if (room.getType() != ERoomType.GROUP) {
                    return;
                }

                groupSettingsRepository.findByGroupId(room.getId()).ifPresent(settings -> {
                    if (settings.isAllowMemberPin()) {
                        return;
                    }
                    if (actorId == null || actorId.isBlank()) {
                        throw new IllegalStateException("Only admins can pin messages in this group.");
                    }

                    UUID actorUuid = UUID.fromString(actorId);
                    RoomMember member = roomMemberRepository.findByRoom_IdAndUser_Id(room.getId(), actorUuid)
                            .orElseThrow(() -> new IllegalStateException("Only group members can pin messages."));
                    boolean isOwner = room.getCreatedBy().getId().equals(actorUuid);
                    boolean isAdmin = member.getRole() == iuh.fit.se.minizalobackend.models.ERoomRole.ADMIN;
                    if (!isOwner && !isAdmin) {
                        throw new IllegalStateException("Only admins can pin messages in this group.");
                    }
                });
            });

            // Permission check for groups
            chatRoomRepository.findById(java.util.UUID.fromString(chatRoomId)).ifPresent(room -> {
                if (room.getType() == ERoomType.GROUP && actorName != null && !actorName.isBlank()
                        && !actorName.equals("Ai đó")) {
                    iuh.fit.se.minizalobackend.models.GroupSettings settings = groupSettingsRepository
                            .findByGroupId(room.getId()).orElse(null);
                    if (settings != null && !settings.isAllowMemberPin()) {
                        // find the sender room member
                        userRepository.findByUsername(actorName).or(() -> userRepository.findByUsername(actorName))
                                .ifPresent(u -> {
                                    roomMemberRepository.findByRoomAndUser(room, u).ifPresent(member -> {
                                        boolean isOwner = room.getCreatedBy().getId().equals(u.getId());
                                        boolean isAdmin = member
                                                .getRole() == iuh.fit.se.minizalobackend.models.ERoomRole.ADMIN;
                                        if (!isOwner && !isAdmin) {
                                            throw new IllegalStateException(
                                                    "Only admins can pin messages in this group.");
                                        }
                                    });
                                });
                    }
                }
            });

            message.setPinned(pin);
            messageDynamoRepository.save(message);

            // Broadcast pin event (trạng thái ghim)
            String pinDestination = "/topic/chat/" + chatRoomId + "/pin";
            messagingTemplate.convertAndSend(pinDestination, Map.of(
                    "messageId", messageId,
                    "isPinned", pin));

            // Broadcast system message vào channel chat chính để cả 2 phía thấy thông báo
            String actor = (actorName != null && !actorName.isBlank()) ? actorName : "Ai đó";
            // Ưu tiên messageType từ request; fallback lấy type từ tin nhắn gốc trong DB
            String msgType = (messageType != null && !messageType.isBlank())
                    ? messageType.toUpperCase()
                    : (message.getType() != null ? message.getType().toUpperCase() : "TEXT");
            String typeLabel;
            switch (msgType) {
                case "IMAGE":
                    typeLabel = "hình ảnh";
                    break;
                case "VIDEO":
                    typeLabel = "video";
                    break;
                case "FILE":
                case "DOCUMENT":
                    typeLabel = "tập tin";
                    break;
                case "LINK":
                    typeLabel = "link";
                    break;
                case "VOICE":
                    typeLabel = "thoại";
                    break;
                default:
                    typeLabel = "văn bản";
                    break;
            }
            String content = pin
                    ? actor + " đã ghim 1 tin nhắn " + typeLabel + "."
                    : actor + " đã bỏ ghim 1 tin nhắn " + typeLabel + ".";

            MessageDynamo sysMsg = new MessageDynamo();
            sysMsg.setMessageId(java.util.UUID.randomUUID().toString());
            sysMsg.setChatRoomId(chatRoomId);
            sysMsg.setSenderId("system");
            sysMsg.setSenderName("Hệ thống");
            sysMsg.setContent(content);
            sysMsg.setType("PIN_NOTIFICATION");
            sysMsg.setCreatedAt(java.time.Instant.now().toString());
            sysMsg.setReplyToMessageId(pin ? messageId : null); // link đến tin nhắn được ghim khi pin
            sysMsg.setRead(false);
            sysMsg.setReadBy(new ArrayList<>());
            sysMsg.setReactions(new ArrayList<>());
            messageDynamoRepository.save(sysMsg);

            String chatDestination = "/topic/chat/" + chatRoomId;
            messagingTemplate.convertAndSend(chatDestination, sysMsg);

            log.info("Message {} {} in room {} by {}", messageId, pin ? "pinned" : "unpinned", chatRoomId, actor);
        });
    }

    @Override
    public PaginatedMessageResult getPinnedMessages(UUID roomId, String lastKey, int limit) {
        log.info("Fetching pinned messages from DynamoDB for room: {}, limit: {}", roomId, limit);
        PaginatedMessageResult result = messageDynamoRepository.getPinnedMessagesByRoomId(roomId.toString(), lastKey,
                limit);
        if (result.getMessages() != null) {
            result.getMessages().forEach(this::normalizeMessage);
        }
        return result;
    }

    private String resolveMessageType(ChatMessageRequest request) {
        if (request.getType() != null && !request.getType().isBlank()) {
            String t = request.getType().trim().toUpperCase();
            if (AppConstants.MESSAGE_TYPE_FOLDER.equals(t)
                    && request.getAttachments() != null
                    && !request.getAttachments().isEmpty()) {
                return AppConstants.MESSAGE_TYPE_FOLDER;
            }
        }
        return determineMessageType(request);
    }

    private String determineMessageType(ChatMessageRequest request) {
        String explicit = request.getType();
        if (explicit != null && !explicit.isBlank()
                && (explicit.startsWith("CALL_") || explicit.equals(AppConstants.MESSAGE_TYPE_SYSTEM))) {
            return explicit;
        }
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
        if (lowerMime.startsWith("audio"))
            return AppConstants.MESSAGE_TYPE_VOICE;
        return AppConstants.MESSAGE_TYPE_DOCUMENT;
    }

    private MessageDynamo normalizeMessage(MessageDynamo message) {
        if (message == null || message.getAttachments() == null)
            return message;
        message.getAttachments().forEach(attachment -> {
            if (attachment.getUrl() != null) {
                attachment.setUrl(minioService.ensurePublicUrl(attachment.getUrl()));
            }
            if (attachment.getThumbnailUrl() != null) {
                attachment.setThumbnailUrl(minioService.ensurePublicUrl(attachment.getThumbnailUrl()));
            }
        });
        return message;
    }

    @Override
    public SearchMessageResponse searchMessages(UUID roomId, String query, int limit, String lastKey,
            String senderId, String fromDateInclusive, String toDateInclusive) {
        SearchMessageResponse result = messageDynamoRepository.searchMessages(roomId.toString(), query, limit, lastKey,
                senderId, fromDateInclusive, toDateInclusive);
        if (result.getMessages() != null) {
            result.getMessages().forEach(this::normalizeMessage);
        }
        return result;
    }

    @Override
    public SearchMessageResponse searchMessagesGlobal(String userId, String query, int limit) {
        if (query == null || query.isBlank()) {
            return new SearchMessageResponse(Collections.emptyList(), null, false, 0);
        }

        // 1. Lấy tất cả phòng của user
        List<RoomMember> memberships = roomMemberRepository.findByUserId(UUID.fromString(userId));
        if (memberships.isEmpty()) {
            return new SearchMessageResponse(Collections.emptyList(), null, false, 0);
        }

        // 2. Search từng phòng (mỗi phòng tối đa 10 kết quả)
        int perRoomLimit = 10;
        List<MessageDynamo> allMatches = new ArrayList<>();

        for (RoomMember membership : memberships) {
            try {
                String roomId = membership.getRoom().getId().toString();
                SearchMessageResponse roomResult = messageDynamoRepository
                        .searchMessages(roomId, query, perRoomLimit, null, null, null, null);
                if (roomResult.getMessages() != null) {
                    java.util.List<MessageDynamo> roomMsgs = new java.util.ArrayList<>(roomResult.getMessages());
                    if (membership.getChatDeletedAt() != null) {
                        final java.time.Instant limitInstant = membership.getChatDeletedAt();
                        roomMsgs.removeIf(m -> isMessageAtOrBefore(m, limitInstant));
                    }
                    final java.time.Instant historyLimit = getEffectiveHistoryVisibleFrom(membership);
                    if (historyLimit != null) {
                        roomMsgs.removeIf(m -> isMessageAtOrBefore(m, historyLimit));
                    }
                    allMatches.addAll(roomMsgs);
                }
            } catch (Exception e) {
                log.warn("[searchMessagesGlobal] Error searching room: {}", e.getMessage());
            }
        }

        // 3. Sắp xếp theo thời gian mới nhất và cắt theo limit
        allMatches.sort((a, b) -> {
            String ta = a.getCreatedAt() != null ? a.getCreatedAt() : "";
            String tb = b.getCreatedAt() != null ? b.getCreatedAt() : "";
            return tb.compareTo(ta);
        });

        List<MessageDynamo> paged = allMatches.size() > limit
                ? allMatches.subList(0, limit)
                : allMatches;

        paged.forEach(this::normalizeMessage);

        boolean hasMore = allMatches.size() > limit;
        log.info("[searchMessagesGlobal] userId={}, query='{}', found={}", userId, query, paged.size());
        return new SearchMessageResponse(paged, null, hasMore, paged.size());
    }

    private boolean isMessageAtOrBefore(MessageDynamo message, java.time.Instant cutoff) {
        if (message == null || cutoff == null || message.getCreatedAt() == null) {
            return false;
        }
        try {
            return !java.time.Instant.parse(message.getCreatedAt()).isAfter(cutoff);
        } catch (Exception e) {
            return false;
        }
    }

    private java.time.Instant getEffectiveHistoryVisibleFrom(RoomMember membership) {
        if (membership == null) {
            return null;
        }
        if (membership.getHistoryVisibleFrom() != null) {
            return membership.getHistoryVisibleFrom();
        }
        try {
            ChatRoom room = membership.getRoom();
            if (room == null || room.getType() != ERoomType.GROUP) {
                return null;
            }
            boolean canReadHistory = groupSettingsRepository.findByGroupId(room.getId())
                    .map(iuh.fit.se.minizalobackend.models.GroupSettings::isAllowNewMemberReadHistory)
                    .orElse(true);
            if (canReadHistory || membership.getJoinedAt() == null) {
                return null;
            }
            return membership.getJoinedAt().atZone(java.time.ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Phòng DIRECT: chặn 2 chiều +
     * {@link #assertRecipientAcceptsDirectMessageFrom}.
     * Không bọc try/catch — mọi lỗi phải lan truyền để không gửi nhầm tin.
     */
    private void enforceDirectChatOutgoingRules(User sender, String roomIdStr) {
        final UUID roomUuid;
        try {
            roomUuid = UUID.fromString(roomIdStr);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid chat room id");
        }
        Optional<ChatRoom> roomOpt = chatRoomRepository.findById(roomUuid);
        if (roomOpt.isEmpty() || roomOpt.get().getType() != ERoomType.DIRECT) {
            return;
        }
        ChatRoom room = roomOpt.get();
        List<RoomMember> members = roomMemberRepository.findAllByRoomWithUsersFetched(room);
        Optional<User> otherUserOpt = members.stream()
                .map(RoomMember::getUser)
                .filter(u -> !u.getId().equals(sender.getId()))
                .findFirst();
        if (otherUserOpt.isEmpty()) {
            return;
        }
        User otherUser = otherUserOpt.get();
        Optional<Friend> senderBlockedOther = friendRepository.findByUserAndFriend(sender, otherUser);
        if (senderBlockedOther.isPresent() && senderBlockedOther.get().getStatus() == EFriendStatus.BLOCKED) {
            throw new IllegalStateException("BLOCKED_BY_YOU");
        }
        Optional<Friend> otherBlockedSender = friendRepository.findByUserAndFriend(otherUser, sender);
        if (otherBlockedSender.isPresent() && otherBlockedSender.get().getStatus() == EFriendStatus.BLOCKED) {
            String blockerName = otherUser.getDisplayName() != null ? otherUser.getDisplayName()
                    : otherUser.getUsername();
            throw new IllegalStateException("BLOCKED_BY_OTHER:" + blockerName);
        }
        User recipient = userRepository.findById(otherUser.getId()).orElse(otherUser);
        assertRecipientAcceptsDirectMessageFrom(sender, recipient);
    }

    private boolean areAcceptedFriends(User a, User b) {
        Optional<Friend> f1 = friendRepository.findByUserAndFriend(a, b);
        if (f1.isPresent() && f1.get().getStatus() == EFriendStatus.ACCEPTED) {
            return true;
        }
        Optional<Friend> f2 = friendRepository.findByUserAndFriend(b, a);
        return f2.isPresent() && f2.get().getStatus() == EFriendStatus.ACCEPTED;
    }

    /**
     * recipient = người nhận tin (đối phương trong phòng DIRECT). Kiểm tra
     * allowMessagesFrom.
     */
    private void assertRecipientAcceptsDirectMessageFrom(User sender, User recipient) {
        EPrivacyAudience policy = recipient.getAllowMessagesFrom() != null
                ? recipient.getAllowMessagesFrom()
                : EPrivacyAudience.EVERYONE;
        if (policy == EPrivacyAudience.EVERYONE) {
            return;
        }
        if (policy == EPrivacyAudience.FRIENDS) {
            if (!areAcceptedFriends(sender, recipient)) {
                throw new IllegalStateException(AppConstants.STRANGER_MESSAGES_NOT_ALLOWED);
            }
            return;
        }
        if (policy == EPrivacyAudience.NO_ONE) {
            throw new IllegalStateException(AppConstants.STRANGER_MESSAGES_NOT_ALLOWED);
        }
    }

    @Override
    public iuh.fit.se.minizalobackend.dtos.response.UnreadContextResponse getUnreadContext(
            java.util.UUID roomId, String userId, int countBefore, int countAfter) {
        String chatRoomId = roomId.toString();

        // B1: Tìm lastReadAt của user để tối ưu việc tìm tin chưa đọc
        String lastReadAtIso = null;
        java.time.Instant chatDeletedAt = null;
        java.time.Instant historyVisibleFrom = null;
        try {
            Optional<ChatRoom> roomOpt = chatRoomRepository.findById(roomId);
            Optional<User> userOpt = userRepository.findById(java.util.UUID.fromString(userId));
            if (roomOpt.isPresent() && userOpt.isPresent()) {
                Optional<RoomMember> memberOpt = roomMemberRepository.findByRoomAndUser(roomOpt.get(), userOpt.get());
                if (memberOpt.isPresent()) {
                    RoomMember membership = memberOpt.get();
                    if (membership.getLastReadAt() != null) {
                        lastReadAtIso = membership.getLastReadAt().atZone(java.time.ZoneOffset.UTC).toInstant().toString();
                    }
                    chatDeletedAt = membership.getChatDeletedAt();
                    historyVisibleFrom = getEffectiveHistoryVisibleFrom(membership);
                }
            }
        } catch (Exception e) {
            log.warn("Could not get lastReadAt for user {} in room {}: {}", userId, chatRoomId, e.getMessage());
        }

        // B1.5: Tìm tin nhắn chưa đọc cũ nhất (bắt đầu từ lastReadAtIso)
        log.info("[getUnreadContext] Searching oldest unread for user {} in room {} since {}", userId, chatRoomId, lastReadAtIso);
        java.util.Optional<MessageDynamo> targetOpt =
                messageDynamoRepository.getOldestUnreadMessage(chatRoomId, userId, lastReadAtIso);

        if (targetOpt.isEmpty()) {
            log.info("[getUnreadContext] No unread messages found for user {} in room {}", userId, chatRoomId);
            return null; // Không có tin chưa đọc
        }

        MessageDynamo target = targetOpt.get();
        if (chatDeletedAt != null) {
            try {
                if (!java.time.Instant.parse(target.getCreatedAt()).isAfter(chatDeletedAt)) {
                    log.info("[getUnreadContext] Oldest unread message is before or at chatDeletedAt. Returning empty.");
                    return null;
                }
            } catch (Exception e) {
                // ignore
            }
        }
        if (historyVisibleFrom != null && isMessageAtOrBefore(target, historyVisibleFrom)) {
            log.info("[getUnreadContext] Oldest unread message is before or at historyVisibleFrom. Returning empty.");
            return null;
        }
        String targetCreatedAt = target.getCreatedAt();

        // B2: Lấy context xung quanh target
        iuh.fit.se.minizalobackend.repository.MessageDynamoRepository.UnreadContextResult ctx =
                messageDynamoRepository.getMessagesAroundTarget(chatRoomId, targetCreatedAt, countBefore, countAfter);

        // B3: Normalize URLs
        normalizeMessage(target);
        ctx.messagesAfter().forEach(this::normalizeMessage);
        ctx.messagesBefore().forEach(this::normalizeMessage);

        java.util.List<MessageDynamo> beforeFiltered = new java.util.ArrayList<>(ctx.messagesBefore());
        if (chatDeletedAt != null) {
            final java.time.Instant limitInstant = chatDeletedAt;
            beforeFiltered.removeIf(m -> isMessageAtOrBefore(m, limitInstant));
        }
        if (historyVisibleFrom != null) {
            final java.time.Instant limitInstant = historyVisibleFrom;
            beforeFiltered.removeIf(m -> isMessageAtOrBefore(m, limitInstant));
        }

        return new iuh.fit.se.minizalobackend.dtos.response.UnreadContextResponse(
                target,
                ctx.messagesAfter(),
                beforeFiltered,
                ctx.hasMoreBefore(),
                ctx.hasMoreAfter()
        );
    }
}
