package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.repository.MessageDynamoRepository;
import iuh.fit.se.minizalobackend.services.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageDynamoRepository messageDynamoRepository;

    @Override
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
        return message;
    }

    @Override
    public PaginatedMessageResult getRoomMessages(UUID roomId, String lastKey, int limit) {
        log.debug("Fetching messages from DynamoDB for room: {}, limit: {}", roomId, limit);
        return messageDynamoRepository.getMessagesByRoomId(roomId.toString(), lastKey, limit);
    }

    @Override
    public void recallMessage(String messageId) {
        log.warn("recallMessage is not yet implemented for DynamoDB!");
    }
}