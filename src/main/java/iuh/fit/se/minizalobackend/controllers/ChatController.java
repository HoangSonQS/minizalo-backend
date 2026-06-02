package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.dtos.request.PinMessageRequest;
import iuh.fit.se.minizalobackend.dtos.request.ReadReceiptRequest;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.dtos.request.TypingIndicatorRequest;
import iuh.fit.se.minizalobackend.dtos.request.ReactionRequest;
import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.payload.request.ChatMessageRequest;
import iuh.fit.se.minizalobackend.payload.request.RecallMessageRequest;
import iuh.fit.se.minizalobackend.services.MessageService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import iuh.fit.se.minizalobackend.services.ChatRoomService;
import iuh.fit.se.minizalobackend.services.UserService;
import iuh.fit.se.minizalobackend.security.services.UserDetailsImpl;
import iuh.fit.se.minizalobackend.utils.AppConstants;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.models.RoomMember;
import iuh.fit.se.minizalobackend.dtos.response.ChatRoomResponse;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;


import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@Slf4j
public class ChatController {


    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomService chatRoomService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final RoomMemberRepository roomMemberRepository;

    public ChatController(MessageService messageService, SimpMessagingTemplate messagingTemplate, ChatRoomService chatRoomService, UserService userService, ObjectMapper objectMapper, RoomMemberRepository roomMemberRepository) {
        this.messageService = messageService;
        this.messagingTemplate = messagingTemplate;
        this.chatRoomService = chatRoomService;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.roomMemberRepository = roomMemberRepository;
    }

    @GetMapping("/api/chat/rooms")
    public ResponseEntity<List<ChatRoomResponse>> getChatRooms(Principal principal) {
        String userId = getUserIdFromPrincipal(principal);
        User user = userService.getUserById(UUID.fromString(userId))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return ResponseEntity.ok(chatRoomService.getChatRoomsForUser(user));
    }

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload @Valid ChatMessageRequest chatMessageRequest, Principal principal) {
        String senderId = getUserIdFromPrincipal(principal);
        log.info("Received message from user: {} to room: {}", senderId, chatMessageRequest.getReceiverId());
        try {
            messageService.processMessage(chatMessageRequest, senderId);
        } catch (IllegalStateException ex) {
            if (AppConstants.STRANGER_MESSAGES_NOT_ALLOWED.equals(ex.getMessage())) {
                String username = getUsernameFromPrincipal(principal);
                // Phải gửi chuỗi JSON: Map gốc dễ bị convert sang dạng không parse được ở @stomp/stompjs → client không gỡ tin temp-.
                try {
                    Map<String, String> err = new LinkedHashMap<>();
                    err.put("code", AppConstants.STRANGER_MESSAGES_NOT_ALLOWED);
                    err.put("roomId", chatMessageRequest.getReceiverId());
                    err.put("text", "Người này hiện không nhận tin nhắn từ người lạ");
                    String json = objectMapper.writeValueAsString(err);
                    messagingTemplate.convertAndSendToUser(username, "/queue/chat-errors", json);
                } catch (JsonProcessingException jpe) {
                    log.error("serialize chat-errors", jpe);
                }
                log.debug("Rejected DM: stranger policy, room {}", chatMessageRequest.getReceiverId());
            } else {
                throw ex;
            }
        }
    }

    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload @Valid TypingIndicatorRequest request, Principal principal) {
        String senderId = getUserIdFromPrincipal(principal);
        String destination = "/topic/typing/" + request.getRoomId();

        messagingTemplate.convertAndSend(destination, Map.of(
                "userId", senderId,
                "isTyping", request.isTyping(),
                "typing", request.isTyping()));
    }

    @MessageMapping("/chat.read")
    public void handleReadReceipt(@Payload @Valid ReadReceiptRequest request, Principal principal) {
        String userId = getUserIdFromPrincipal(principal);
        messageService.markMessageAsRead(request.getRoomId(), request.getMessageId(), userId);
    }

    @MessageMapping("/chat.reaction")
    public void handleReaction(@Payload @Valid ReactionRequest request, Principal principal) {
        String userId = getUserIdFromPrincipal(principal);
        messageService.addReaction(request.getRoomId(), request.getMessageId(), userId, request.getEmoji());
    }

    @PutMapping("/api/chat/{roomId}/messages/{messageId}/reactions")
    public ResponseEntity<Void> setReaction(
            @PathVariable String roomId,
            @PathVariable String messageId,
            @RequestBody Map<String, Object> body,
            Principal principal) {
        String userId = getUserIdFromPrincipal(principal);
        Object emojiObj = body != null ? body.get("emoji") : null;
        String emoji = emojiObj instanceof String ? (String) emojiObj : null;
        if (emoji == null || emoji.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        messageService.addReaction(roomId, messageId, userId, emoji);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/chat/{roomId}/messages/{messageId}/reactions")
    public ResponseEntity<Void> removeReaction(
            @PathVariable String roomId,
            @PathVariable String messageId,
            Principal principal) {
        String userId = getUserIdFromPrincipal(principal);
        messageService.removeReaction(roomId, messageId, userId);
        return ResponseEntity.ok().build();
    }

    @MessageMapping("/chat.pin")
    public void handlePinMessage(@Payload @Valid PinMessageRequest request, Principal principal) {
        String actorId = null;
        try {
            actorId = getUserIdFromPrincipal(principal);
            User actor = userService.getUserById(UUID.fromString(actorId)).orElse(null);
            String actorName = actor != null
                    ? (actor.getDisplayName() != null ? actor.getDisplayName() : actor.getUsername())
                    : "Ai đó";
            messageService.pinMessage(
                    request.getRoomId(),
                    request.getMessageId(),
                    request.isPin(),
                    actorId,
                    actorName,
                    request.getMessageType()
            );
        } catch (IllegalStateException e) {
            String dest = "/topic/chat/" + request.getRoomId() + "/pin";
            messagingTemplate.convertAndSend(dest, Map.of(
                    "error", true,
                    "actorId", actorId != null ? actorId : "",
                    "message", e.getMessage() != null ? e.getMessage() : "Không thể ghim tin nhắn"
            ));
        }
    }

    @DeleteMapping("/api/chat/history/{roomId}")
    public ResponseEntity<Void> clearChatHistory(
            @PathVariable UUID roomId,
            Principal principal) {
        String currentUserId = getUserIdFromPrincipal(principal);
        UUID userId = UUID.fromString(currentUserId);
        log.info("User {} clearing history for room: {}", currentUserId, roomId);

        RoomMember membership = roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));

        membership.setChatDeletedAt(java.time.Instant.now());
        roomMemberRepository.save(membership);

        notifyRoomListChanged(userId, roomId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/chat/rooms/{roomId}")
    public ResponseEntity<Void> deleteChatRoom(
            @PathVariable UUID roomId,
            Principal principal) {
        String currentUserId = getUserIdFromPrincipal(principal);
        User actor = userService.getUserById(UUID.fromString(currentUserId))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        log.info("User {} deleting chat room: {}", currentUserId, roomId);
        chatRoomService.deleteChatRoom(roomId, actor);
        return ResponseEntity.noContent().build();
    }

    /** Khôi phục cuộc trò chuyện đã bị ẩn (reset chatDeletedAt) — dùng khi mở lại nhóm từ tìm kiếm */
    @PostMapping("/api/chat/rooms/{roomId}/restore")
    public ResponseEntity<Void> restoreChatRoom(
            @PathVariable UUID roomId,
            Principal principal) {
        String currentUserId = getUserIdFromPrincipal(principal);
        UUID userId = UUID.fromString(currentUserId);
        log.info("User {} restoring chat room: {}", currentUserId, roomId);

        RoomMember membership = roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "You are not a member of this room."));

        if (membership.getChatDeletedAt() != null) {
            membership.setChatDeletedAt(null);
            roomMemberRepository.save(membership);
            log.info("Restored room {} for user {}", roomId, currentUserId);
        }

        notifyRoomListChanged(userId, roomId);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/api/chat/history/{roomId}")
    public ResponseEntity<PaginatedMessageResult> getChatHistory(
            @PathVariable UUID roomId,
            @RequestParam(required = false) String lastKey,
            @RequestParam(defaultValue = "20") int limit,
            Principal principal) {
        String currentUserId = getUserIdFromPrincipal(principal);
        UUID userId = UUID.fromString(currentUserId);
        RoomMember membership = roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        log.info("Fetching history for room: {}, limit: {}, user: {}", roomId, limit, currentUserId);
        PaginatedMessageResult result = messageService.getRoomMessages(roomId, lastKey, limit);
        if (result.getMessages() != null) {
            java.util.List<MessageDynamo> filtered = new java.util.ArrayList<>(result.getMessages());
            filtered.removeIf(m ->
                    m.isPrivacyBlocked() && !currentUserId.equals(m.getSenderId()));
            if (membership.getChatDeletedAt() != null) {
                final java.time.Instant limitInstant = membership.getChatDeletedAt();
                filtered.removeIf(m -> isMessageAtOrBefore(m, limitInstant));
            }
            if (membership.getHistoryVisibleFrom() != null) {
                final java.time.Instant limitInstant = membership.getHistoryVisibleFrom();
                filtered.removeIf(m -> isMessageAtOrBefore(m, limitInstant));
            }
            result = new PaginatedMessageResult(filtered, result.getLastEvaluatedKey());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/chat/{roomId}/pins")
    public ResponseEntity<PaginatedMessageResult> getPinnedMessages(
            @PathVariable UUID roomId,
            @RequestParam(required = false) String lastKey,
            @RequestParam(defaultValue = "20") int limit,
            Principal principal) {
        String currentUserId = getUserIdFromPrincipal(principal);
        UUID userId = UUID.fromString(currentUserId);
        RoomMember membership = roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        log.info("Fetching pinned messages for room: {}, limit: {}", roomId, limit);
        PaginatedMessageResult result = messageService.getPinnedMessages(roomId, lastKey, limit);
        if (result.getMessages() != null) {
            java.util.List<MessageDynamo> filtered = new java.util.ArrayList<>(result.getMessages());
            if (membership.getChatDeletedAt() != null) {
                final java.time.Instant limitInstant = membership.getChatDeletedAt();
                filtered.removeIf(m -> isMessageAtOrBefore(m, limitInstant));
            }
            if (membership.getHistoryVisibleFrom() != null) {
                final java.time.Instant limitInstant = membership.getHistoryVisibleFrom();
                filtered.removeIf(m -> isMessageAtOrBefore(m, limitInstant));
            }
            result = new PaginatedMessageResult(filtered, result.getLastEvaluatedKey());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/chat/rooms/private/{userId}")
    public ResponseEntity<ChatRoomResponse> createDirectChat(
            @PathVariable UUID userId,
            Principal principal) {
        String currentUserId = getUserIdFromPrincipal(principal);
        User user1 = userService.getUserById(UUID.fromString(currentUserId))
                .orElseThrow(() -> new UsernameNotFoundException("Current user not found"));
        User user2 = userService.getUserById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Partner user not found"));

        return ResponseEntity.ok(chatRoomService.createDirectChat(user1, user2));
    }

    @PostMapping("/api/chat/send")
    public ResponseEntity<Map<String, Object>> sendMessageRest(
            @Valid @RequestBody ChatMessageRequest chatMessageRequest,
            Principal principal) {
        String senderId = getUserIdFromPrincipal(principal);
        log.info("REST send message from user: {} to room: {}", senderId, chatMessageRequest.getReceiverId());
        MessageDynamo message;
        try {
            message = messageService.processMessage(chatMessageRequest, senderId);
        } catch (IllegalStateException ex) {
            if (AppConstants.STRANGER_MESSAGES_NOT_ALLOWED.equals(ex.getMessage())) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("code", AppConstants.STRANGER_MESSAGES_NOT_ALLOWED);
                err.put("roomId", chatMessageRequest.getReceiverId());
                err.put("message", "Người này hiện không nhận tin nhắn từ người lạ");
                return ResponseEntity.status(403).body(err);
            }
            throw ex;
        }
        
        // Convert to Map to avoid ClassCastException with Spring DevTools RestartClassLoader
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("messageId", message.getMessageId());
        response.put("chatRoomId", message.getChatRoomId());
        response.put("senderId", message.getSenderId());
        response.put("senderName", message.getSenderName());
        response.put("content", message.getContent());
        response.put("type", message.getType());
        response.put("createdAt", message.getCreatedAt());
        response.put("read", message.isRead());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/messages")
    public ResponseEntity<List<?>> getMessages(
            @RequestParam String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        return ResponseEntity.ok(List.of());
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

    private String getUserIdFromPrincipal(Principal principal) {
        if (principal == null) {
            throw new IllegalStateException("WebSocket session not authenticated – JWT may be expired or missing");
        }
        if (principal instanceof UsernamePasswordAuthenticationToken) {
            Object p = ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
            if (p instanceof UserDetailsImpl) {
                return ((UserDetailsImpl) p).getId().toString();
            }
        }
        return principal.getName();
    }

    private String getUsernameFromPrincipal(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken) {
            Object p = ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
            if (p instanceof UserDetailsImpl) {
                return ((UserDetailsImpl) p).getUsername();
            }
        }
        throw new IllegalStateException("Cannot resolve username for WebSocket principal");
    }

    @PostMapping("/api/chat/forward")
    public ResponseEntity<MessageDynamo> forwardMessage(
            @Valid @RequestBody iuh.fit.se.minizalobackend.dtos.request.ForwardMessageRequest request,
            Principal principal) {
        String senderId = getUserIdFromPrincipal(principal);
        MessageDynamo forwarded = messageService.forwardMessage(
                request.getOriginalRoomId(),
                request.getOriginalMessageId(),
                request.getTargetRoomId(),
                senderId);
        return ResponseEntity.ok(forwarded);
    }

    @PostMapping("/api/messages/recall")
    public void recallMessage(@RequestBody RecallMessageRequest recallMessageRequest, Principal principal) {
        String requesterId = getUserIdFromPrincipal(principal);
        messageService.recallMessage(recallMessageRequest.getRoomId(), recallMessageRequest.getMessageId(), requesterId);
    }

    @DeleteMapping("/api/messages/cloud/{roomId}/{messageId}")
    public ResponseEntity<Void> deleteCloudMessage(
            @PathVariable String roomId,
            @PathVariable String messageId,
            Principal principal) {
        String requesterId = getUserIdFromPrincipal(principal);
        messageService.deleteCloudMessage(roomId, messageId, requesterId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/messages/cloud/{roomId}/media/delete")
    public ResponseEntity<Void> deleteCloudMediaItems(
            @PathVariable String roomId,
            @RequestBody Map<String, List<Map<String, String>>> request,
            Principal principal) {
        String requesterId = getUserIdFromPrincipal(principal);
        messageService.deleteCloudMediaItems(roomId, request.get("items"), requesterId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/chat/{roomId}/search")
    public ResponseEntity<iuh.fit.se.minizalobackend.dtos.response.SearchMessageResponse> searchMessages(
            @PathVariable UUID roomId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String lastKey,
            @RequestParam(required = false) String senderId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            Principal principal) {
        String currentUserId = getUserIdFromPrincipal(principal);
        UUID userId = UUID.fromString(currentUserId);
        RoomMember membership = roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        log.info("Searching messages in room: {}, q={}, senderId={}, fromDate={}, toDate={}", roomId, q, senderId,
                fromDate, toDate);
        iuh.fit.se.minizalobackend.dtos.response.SearchMessageResponse resp =
                messageService.searchMessages(roomId, q, limit, lastKey, senderId, fromDate, toDate);
        if (resp != null && resp.getMessages() != null && membership.getChatDeletedAt() != null) {
            java.util.List<MessageDynamo> filtered = new java.util.ArrayList<>(resp.getMessages());
            final java.time.Instant limitInstant = membership.getChatDeletedAt();
            filtered.removeIf(m -> isMessageAtOrBefore(m, limitInstant));
            resp.setMessages(filtered);
            resp.setTotalResults(filtered.size());
        }
        if (resp != null && resp.getMessages() != null && membership.getHistoryVisibleFrom() != null) {
            java.util.List<MessageDynamo> filtered = new java.util.ArrayList<>(resp.getMessages());
            final java.time.Instant limitInstant = membership.getHistoryVisibleFrom();
            filtered.removeIf(m -> isMessageAtOrBefore(m, limitInstant));
            resp.setMessages(filtered);
            resp.setTotalResults(filtered.size());
        }
        return ResponseEntity.ok(resp);
    }

    /**
     * Tìm kiếm tin nhắn toàn cục (across all rooms user belongs to).
     * GET /api/messages/search?q=keyword&limit=20
     */
    @GetMapping("/api/messages/search")
    public ResponseEntity<iuh.fit.se.minizalobackend.dtos.response.SearchMessageResponse> searchMessagesGlobal(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit,
            Principal principal) {
        String userId = getUserIdFromPrincipal(principal);
        log.info("Global search messages, userId={}, query='{}', limit={}", userId, q, limit);
        return ResponseEntity.ok(messageService.searchMessagesGlobal(userId, q, limit));
    }

    /**
     * Lưu tên gợi nhớ (nickname) cho 1 phòng chat — chỉ hiển thị với user đặt.
     * PUT /api/chat/rooms/{roomId}/nickname
     * Body: { "nickname": "Tên mới" }
     */
    @PutMapping("/api/chat/rooms/{roomId}/nickname")
    public ResponseEntity<iuh.fit.se.minizalobackend.dtos.response.ChatRoomResponse> saveNickname(
            @PathVariable UUID roomId,
            @RequestBody Map<String, String> body,
            Principal principal) {
        String userId = getUserIdFromPrincipal(principal);
        User actor = userService.getUserById(UUID.fromString(userId))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        String nickname = body.getOrDefault("nickname", "");
        iuh.fit.se.minizalobackend.dtos.response.ChatRoomResponse updated =
                chatRoomService.saveNickname(roomId, nickname, actor);
        notifyRoomListChanged(actor.getId(), roomId);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/api/chat/rooms/{roomId}/wallpaper")
    public ResponseEntity<iuh.fit.se.minizalobackend.dtos.response.ChatRoomResponse> updateWallpaper(
            @PathVariable UUID roomId,
            @RequestBody(required = false) Map<String, String> body,
            Principal principal) {
        String userId = getUserIdFromPrincipal(principal);
        User actor = userService.getUserById(UUID.fromString(userId))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        String wallpaperUrl = body != null ? body.getOrDefault("wallpaperUrl", "") : "";
        log.info("Updating chat wallpaper for room {} by user {}", roomId, actor.getId());
        iuh.fit.se.minizalobackend.dtos.response.ChatRoomResponse updated =
                chatRoomService.updateWallpaper(roomId, wallpaperUrl, actor);
        if (updated.getMembers() != null) {
            updated.getMembers().forEach(member -> {
                if (member.getUser() != null && member.getUser().getId() != null) {
                    notifyRoomListChanged(member.getUser().getId(), roomId);
                }
            });
        }
        return ResponseEntity.ok(updated);
    }

    private void notifyRoomListChanged(UUID userId, UUID roomId) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/rooms",
                    Map.of("action", "UNREAD_UPDATE", "roomId", roomId.toString())
            );
        } catch (Exception ex) {
            log.warn("Could not notify room list update for user {} room {}: {}", userId, roomId, ex.getMessage());
        }
    }

    /**
     * GET /api/chat/{roomId}/unread-context
     * Trả về tin nhắn chưa đọc cũ nhất + context xung quanh để FlatList scroll chính xác.
     * @param countBefore số tin cũ hơn target cần trả về (default 5)
     * @param countAfter  số tin mới hơn target cần trả về (default 15)
     */
    @GetMapping("/api/chat/{roomId}/unread-context")
    public ResponseEntity<iuh.fit.se.minizalobackend.dtos.response.UnreadContextResponse> getUnreadContext(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "5") int countBefore,
            @RequestParam(defaultValue = "15") int countAfter,
            Principal principal) {
        String userId = getUserIdFromPrincipal(principal);
        UUID userUuid = UUID.fromString(userId);
        if (!roomMemberRepository.existsByRoom_IdAndUser_Id(roomId, userUuid)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        log.info("Fetching unread context for room: {}, user: {}", roomId, userId);
        iuh.fit.se.minizalobackend.dtos.response.UnreadContextResponse ctx =
                messageService.getUnreadContext(roomId, userId, countBefore, countAfter);
        if (ctx == null) {
            return ResponseEntity.noContent().build(); // 204 = không có tin chưa đọc
        }
        return ResponseEntity.ok(ctx);
    }
}

