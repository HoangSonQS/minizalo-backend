package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.models.Message;
import iuh.fit.se.minizalobackend.payload.request.ChatMessageRequest;
import iuh.fit.se.minizalobackend.payload.request.RecallMessageRequest;
import iuh.fit.se.minizalobackend.payload.response.ChatMessageResponse;
import iuh.fit.se.minizalobackend.services.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class ChatController {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final MessageService messageService;

    public ChatController(SimpMessagingTemplate simpMessagingTemplate, MessageService messageService) {
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.messageService = messageService;
    }

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload @Valid ChatMessageRequest chatMessageRequest, Principal principal) {

        String senderId = getUserIdFromPrincipal(principal);

        Message message = new Message();
        message.setFromUserId(senderId);
        message.setToUserId(chatMessageRequest.getReceiverId());
        message.setContent(chatMessageRequest.getContent());
        message.setConversationId(getConversationId(senderId, chatMessageRequest.getReceiverId()));

        Message savedMessage = messageService.saveMessage(message);

        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(savedMessage.getId())
                .senderId(savedMessage.getFromUserId())
                .receiverId(savedMessage.getToUserId())
                .content(savedMessage.getContent())
                .createdAt(savedMessage.getCreatedAt())
                .recalled(savedMessage.isRecalled())
                .build();

        // Publish to receiver
        simpMessagingTemplate.convertAndSend("/topic/user/" + chatMessageRequest.getReceiverId(), response);
        // Publish to sender (confirmation)
        simpMessagingTemplate.convertAndSend("/topic/user/" + senderId, response);
    }

    @GetMapping("/api/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            @RequestParam String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {

        String currentUserId = getUserIdFromPrincipal(principal);
        String conversationId = getConversationId(currentUserId, userId);

        List<Message> messages = messageService.getMessages(conversationId, page, size);

        List<ChatMessageResponse> response = messages.stream()
                .map(msg -> ChatMessageResponse.builder()
                        .id(msg.getId())
                        .senderId(msg.getFromUserId())
                        .receiverId(msg.getToUserId())
                        .content(msg.getContent())
                        .createdAt(msg.getCreatedAt())
                        .recalled(msg.isRecalled())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // Helper to extract ID
    private String getUserIdFromPrincipal(Principal principal) {
        if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken) {
            Object p = ((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) principal)
                    .getPrincipal();
            if (p instanceof iuh.fit.se.minizalobackend.security.services.UserDetailsImpl) {
                return ((iuh.fit.se.minizalobackend.security.services.UserDetailsImpl) p).getId().toString();
            }
        }
        return principal.getName(); // Fallback
    }

    private String getConversationId(String userId1, String userId2) {
        return userId1.compareTo(userId2) > 0 ? userId1 + "_" + userId2 : userId2 + "_" + userId1;
    }

    // Keeping recall for backward compatibility or updating it
    @PostMapping("/messages/recall")
    public void recallMessage(@RequestBody RecallMessageRequest recallMessageRequest) {
        messageService.recallMessage(recallMessageRequest.getMessageId());
    }

}
