package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.models.Message;
import iuh.fit.se.minizalobackend.repository.MessageRepository;
import iuh.fit.se.minizalobackend.services.MessageService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
        message.setCreatedAt(LocalDateTime.now());
        message.setRecalled(false);
        return messageRepository.save(message);
    }

    @Override
    public List<Message> getMessages(String conversationId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable).getContent();
    }

    @Override
    public void recallMessage(String messageId) {
        try {
            UUID uuid = UUID.fromString(messageId);
            Message message = messageRepository.findById(uuid).orElse(null);
            if (message != null) {
                message.setRecalled(true);
                messageRepository.save(message);
            }
        } catch (IllegalArgumentException e) {
            // Handle invalid UUID string
        }
    }
}
