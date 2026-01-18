package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.models.ChatMessage;
import iuh.fit.se.minizalobackend.models.Message;
import iuh.fit.se.minizalobackend.services.MessageService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ChatController {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final MessageService messageService;

    public ChatController(SimpMessagingTemplate simpMessagingTemplate, MessageService messageService) {
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.messageService = messageService;
    }

    @MessageMapping("/chat")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        Message message = new Message();
        message.setFromUserId(chatMessage.getFrom());
        message.setToUserId(chatMessage.getTo());
        message.setContent(chatMessage.getContent());
        message.setConversationId(getConversationId(chatMessage.getFrom(), chatMessage.getTo()));

        Message savedMessage = messageService.saveMessage(message);
        chatMessage.setTimestamp(savedMessage.getTimestamp());

        simpMessagingTemplate.convertAndSendToUser(chatMessage.getTo(), "/queue/messages", chatMessage);
    }

    @GetMapping("/messages/{fromUserId}/{toUserId}")
    public List<Message> getMessages(@PathVariable String fromUserId, @PathVariable String toUserId) {
        return messageService.getMessages(getConversationId(fromUserId, toUserId));
    }

    @PostMapping("/messages/recall")
    public void recallMessage(@RequestBody RecallMessageRequest recallMessageRequest) {
        messageService.recallMessage(
                getConversationId(recallMessageRequest.getFromUserId(), recallMessageRequest.getToUserId()),
                recallMessageRequest.getMessageId());
    }

    private String getConversationId(String userId1, String userId2) {
        return userId1.compareTo(userId2) > 0 ? userId1 + "_" + userId2 : userId2 + "_" + userId1;
    }

    private static class RecallMessageRequest {
        private String fromUserId;
        private String toUserId;
        private String messageId;

        public String getFromUserId() {
            return fromUserId;
        }

        public String getToUserId() {
            return toUserId;
        }

        public String getMessageId() {
            return messageId;
        }
    }
}
