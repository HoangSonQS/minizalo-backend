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

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@Slf4j
public class ChatController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(MessageService messageService, SimpMessagingTemplate messagingTemplate) {
        this.messageService = messageService;
        this.messagingTemplate = messagingTemplate;
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

    @MessageMapping("/chat.pin")
    public void handlePinMessage(@Payload @Valid PinMessageRequest request, Principal principal) {
        messageService.pinMessage(request.getRoomId(), request.getMessageId(), request.isPin());
    }

    @GetMapping("/api/chat/history/{roomId}")
    public ResponseEntity<PaginatedMessageResult> getChatHistory(
            @PathVariable UUID roomId,
            @RequestParam(required = false) String lastKey,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("Fetching history for room: {}, limit: {}", roomId, limit);
        PaginatedMessageResult result = messageService.getRoomMessages(roomId, lastKey, limit);
        return ResponseEntity.ok(result);
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

    @PostMapping("/messages/recall")
    public void recallMessage(@RequestBody RecallMessageRequest recallMessageRequest) {
        messageService.recallMessage(recallMessageRequest.getRoomId(), recallMessageRequest.getMessageId());
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
}
