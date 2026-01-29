package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.models.MessageDynamo;

public interface MessageDynamoRepository {
    void save(MessageDynamo message);

    PaginatedMessageResult getMessagesByRoomId(String chatRoomId, String lastEvaluatedKey, int limit);

    java.util.Optional<MessageDynamo> getMessage(String chatRoomId, String messageId);
}