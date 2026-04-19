package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.dtos.response.SearchMessageResponse;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.payload.request.ChatMessageRequest;
import iuh.fit.se.minizalobackend.models.EFriendStatus;
import iuh.fit.se.minizalobackend.models.Friend;
import iuh.fit.se.minizalobackend.models.MessageReaction;
import iuh.fit.se.minizalobackend.models.RoomMember;
import iuh.fit.se.minizalobackend.models.ChatRoom;
import iuh.fit.se.minizalobackend.models.ERoomType;
import iuh.fit.se.minizalobackend.repository.ChatRoomRepository;
import iuh.fit.se.minizalobackend.repository.FriendRepository;
import iuh.fit.se.minizalobackend.repository.MessageDynamoRepository;
import iuh.fit.se.minizalobackend.repository.GroupRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.services.NotificationService;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;
    private final iuh.fit.se.minizalobackend.services.MinioService minioService;

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

        // Log activity
        analyticsService.logActivity(UUID.fromString(message.getSenderId()), AppConstants.ACTIVITY_MESSAGE_SENT,
                "Message sent to room: " + message.getChatRoomId());

        // Trigger notifications for offline members
        triggerNotifications(message);

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

        // ── Block check for DIRECT chat rooms ──
        boolean strangerPrivacyBlocked = false;
        String roomIdStr = request.getReceiverId();
        try {
            UUID roomUuid = UUID.fromString(roomIdStr);
            Optional<ChatRoom> roomOpt = chatRoomRepository.findById(roomUuid);
            if (roomOpt.isPresent() && roomOpt.get().getType() == ERoomType.DIRECT) {
                List<RoomMember> members = roomMemberRepository.findAllByRoom(roomOpt.get());
                // Find the other participant
                Optional<User> otherUserOpt = members.stream()
                        .map(RoomMember::getUser)
                        .filter(u -> !u.getId().toString().equals(senderId))
                        .findFirst();
                if (otherUserOpt.isPresent()) {
                    User otherUser = otherUserOpt.get();
                    // Check if sender blocked other user
                    Optional<Friend> senderBlockedOther = friendRepository.findByUserAndFriend(sender, otherUser);
                    if (senderBlockedOther.isPresent()
                            && senderBlockedOther.get().getStatus() == EFriendStatus.BLOCKED) {
                        throw new IllegalStateException("BLOCKED_BY_YOU");
                    }
                    // Check if other user blocked sender
                    Optional<Friend> otherBlockedSender = friendRepository.findByUserAndFriend(otherUser, sender);
                    if (otherBlockedSender.isPresent()
                            && otherBlockedSender.get().getStatus() == EFriendStatus.BLOCKED) {
                        String blockerName = otherUser.getDisplayName() != null ? otherUser.getDisplayName()
                                : otherUser.getUsername();
                        throw new IllegalStateException("BLOCKED_BY_OTHER:" + blockerName);
                    }

                    // --- STRANGER PRIVACY CHECK ---
                    boolean areFriends =
                            (senderBlockedOther.isPresent() && senderBlockedOther.get().getStatus() == EFriendStatus.ACCEPTED)
                            || (otherBlockedSender.isPresent() && otherBlockedSender.get().getStatus() == EFriendStatus.ACCEPTED);

                    if (!areFriends && Boolean.FALSE.equals(otherUser.getAllowStrangerMessages())) {
                        strangerPrivacyBlocked = true;
                    }
                    // ------------------------------------------
                }
            }
        } catch (IllegalStateException e) {
            throw e; // re-throw block & limit exceptions
        } catch (Exception e) {
            log.warn("Block check failed (non-critical): {}", e.getMessage());
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
        message.setType(determineMessageType(request));
        message.setCreatedAt(Instant.now().toString());
        message.setReplyToMessageId(request.getReplyToMessageId());
        message.setRead(false);
        message.setReadBy(new ArrayList<>());
        message.setReactions(new ArrayList<>());

        log.info("[DEBUG] ProcessMessage attachments count: {}", 
                 message.getAttachments() != null ? message.getAttachments().size() : "null");

        if (strangerPrivacyBlocked) {
            message.setPrivacyBlocked(true);
        }

        saveMessage(message);

        if (strangerPrivacyBlocked) {
            // Only send back to sender's personal queue, not the whole room
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
                        // Luôn gửi push khi có token: WS vẫn coi user "online" khi app chạy nền → trước đây không có FCM.
                        String fcmToken = member.getUser().getFcmToken();
                        if (fcmToken != null && !fcmToken.isEmpty()) {
                            log.debug("Sending push notification to user: {}", recipientId);
                            notificationService.sendNotification(
                                    recipientId,
                                    fcmToken,
                                    "New Message",
                                    "You have a new message from " + message.getSenderName(),
                                    message.getChatRoomId(),
                                    message.getSenderName());
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
        messageDynamoRepository.getMessage(chatRoomId, messageId).ifPresent(message -> {
            if (pin && !message.isPinned()) {
                long pinnedCount = messageDynamoRepository.countPinnedMessages(chatRoomId);
                if (pinnedCount >= 5) {
                    throw new IllegalStateException("Chỉ được pin tối đa 5 tin nhắn trong một cuộc trò chuyện.");
                }
            }
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

    @Override
    public PaginatedMessageResult getPinnedMessages(UUID roomId, String lastKey, int limit) {
        log.info("Fetching pinned messages from DynamoDB for room: {}, limit: {}", roomId, limit);
        PaginatedMessageResult result = messageDynamoRepository.getPinnedMessagesByRoomId(roomId.toString(), lastKey, limit);
        if (result.getMessages() != null) {
            result.getMessages().forEach(this::normalizeMessage);
        }
        return result;
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
        return AppConstants.MESSAGE_TYPE_DOCUMENT;
    }

    private MessageDynamo normalizeMessage(MessageDynamo message) {
        if (message == null || message.getAttachments() == null) return message;
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
    public SearchMessageResponse searchMessages(UUID roomId, String query, int limit, String lastKey) {
        SearchMessageResponse result = messageDynamoRepository.searchMessages(roomId.toString(), query, limit, lastKey);
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
                        .searchMessages(roomId, query, perRoomLimit, null);
                allMatches.addAll(roomResult.getMessages());
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
}