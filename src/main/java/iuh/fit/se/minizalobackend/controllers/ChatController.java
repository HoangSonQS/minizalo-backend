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
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.dtos.response.ChatRoomResponse;
import org.springframework.security.core.userdetails.UsernameNotFoundException;


import java.security.Principal;
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

    public ChatController(MessageService messageService, SimpMessagingTemplate messagingTemplate, ChatRoomService chatRoomService, UserService userService) {
        this.messageService = messageService;
        this.messagingTemplate = messagingTemplate;
        this.chatRoomService = chatRoomService;
        this.userService = userService;
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
        messageService.processMessage(chatMessageRequest, senderId);
    }

    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload @Valid TypingIndicatorRequest request, Principal principal) {
        String senderId = getUserIdFromPrincipal(principal);
        String destination = "/topic/typing/" + request.getRoomId();

        messagingTemplate.convertAndSend(destination, Map.of(
                "userId", senderId,
                "isTyping", request.isTyping()));
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
        try {
            messageService.pinMessage(request.getRoomId(), request.getMessageId(), request.isPin());
        } catch (IllegalStateException e) {
            String userId = getUserIdFromPrincipal(principal);
            String dest = "/topic/chat/" + request.getRoomId() + "/pin";
            messagingTemplate.convertAndSend(dest, Map.of(
                    "error", true,
                    "message", e.getMessage() != null ? e.getMessage() : "Không thể ghim tin nhắn"
            ));
        }
    }

    @GetMapping("/api/chat/history/{roomId}")
    public ResponseEntity<PaginatedMessageResult> getChatHistory(
            @PathVariable UUID roomId,
            @RequestParam(required = false) String lastKey,
            @RequestParam(defaultValue = "20") int limit,
            Principal principal) {
        String currentUserId = getUserIdFromPrincipal(principal);
        log.info("Fetching history for room: {}, limit: {}, user: {}", roomId, limit, currentUserId);
        PaginatedMessageResult result = messageService.getRoomMessages(roomId, lastKey, limit);
        if (result.getMessages() != null) {
            java.util.List<MessageDynamo> filtered = new java.util.ArrayList<>(result.getMessages());
            filtered.removeIf(m ->
                    m.isPrivacyBlocked() && !currentUserId.equals(m.getSenderId()));
            result = new PaginatedMessageResult(filtered, result.getLastEvaluatedKey());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/chat/{roomId}/pins")
    public ResponseEntity<PaginatedMessageResult> getPinnedMessages(
            @PathVariable UUID roomId,
            @RequestParam(required = false) String lastKey,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("Fetching pinned messages for room: {}, limit: {}", roomId, limit);
        PaginatedMessageResult result = messageService.getPinnedMessages(roomId, lastKey, limit);
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
        MessageDynamo message = messageService.processMessage(chatMessageRequest, senderId);
        
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

    private String getUserIdFromPrincipal(Principal principal) {
        if (principal == null) {
            throw new IllegalStateException("WebSocket session not authenticated – JWT may be expired or missing");
        }
        if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken) {
            Object p = ((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) principal)
                    .getPrincipal();
            if (p instanceof iuh.fit.se.minizalobackend.security.services.UserDetailsImpl) {
                return ((iuh.fit.se.minizalobackend.security.services.UserDetailsImpl) p).getId().toString();
            }
        }
        return principal.getName();
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

    @GetMapping("/api/chat/{roomId}/search")
    public ResponseEntity<iuh.fit.se.minizalobackend.dtos.response.SearchMessageResponse> searchMessages(
            @PathVariable UUID roomId,
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String lastKey) {
        log.info("Searching messages in room: {}, query: {}", roomId, q);
        return ResponseEntity.ok(messageService.searchMessages(roomId, q, limit, lastKey));
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
        return ResponseEntity.ok(updated);
    }
}
