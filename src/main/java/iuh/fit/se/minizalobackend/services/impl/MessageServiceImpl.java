package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.models.Message;
import iuh.fit.se.minizalobackend.repository.MessageRepository;
import iuh.fit.se.minizalobackend.services.MessageService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;

    public MessageServiceImpl(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Override
    public Message saveMessage(Message message) {
        message.setMessageId(UUID.randomUUID().toString());
        message.setTimestamp(LocalDateTime.now());
        messageRepository.save(message);
        return message;
    }

    @Override
    public List<Message> getMessages(String conversationId) {
        return messageRepository.findByConversationId(conversationId);
    }

    @Override
    public void recallMessage(String conversationId, String messageId) {
        Message message = messageRepository.findById(conversationId, messageId);
        if (message != null) {
            message.setRecalled(true);
            messageRepository.save(message);
        }
    }
}
