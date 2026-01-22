package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.models.MessageDynamo;

import java.util.UUID;

public interface MessageService {
    MessageDynamo saveMessage(MessageDynamo message);

    PaginatedMessageResult getRoomMessages(UUID roomId, String lastKey, int limit);

    void recallMessage(String messageId);
}