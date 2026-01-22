package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.payload.request.ChatMessageRequest;
import iuh.fit.se.minizalobackend.payload.request.RecallMessageRequest;
import iuh.fit.se.minizalobackend.services.MessageService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@Slf4j
public class ChatController {

    private final MessageService messageService;

    public ChatController(MessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload @Valid ChatMessageRequest chatMessageRequest, Principal principal) {
        String senderId = getUserIdFromPrincipal(principal);
        log.info("Received message from user: {} to user: {}", senderId, chatMessageRequest.getReceiverId());
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
        // This endpoint is deprecated in favor of /api/chat/history/{roomId}
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

    @PostMapping("/messages/recall")
    public void recallMessage(@RequestBody RecallMessageRequest recallMessageRequest) {
        messageService.recallMessage(recallMessageRequest.getMessageId());
    }

}
