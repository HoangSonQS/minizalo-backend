package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.dtos.response.SearchMessageResponse;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.payload.request.ChatMessageRequest;
import java.util.UUID;

public interface MessageService {
    MessageDynamo saveMessage(MessageDynamo message);

    PaginatedMessageResult getRoomMessages(UUID roomId, String lastKey, int limit);

    void recallMessage(String chatRoomId, String messageId);

    void markMessageAsRead(String chatRoomId, String messageId, String userId);

    void addReaction(String chatRoomId, String messageId, String userId, String emoji);

    void pinMessage(String chatRoomId, String messageId, boolean pin);

    MessageDynamo forwardMessage(String originalRoomId, String originalMessageId, String targetRoomId, String senderId);

    MessageDynamo processMessage(ChatMessageRequest request, String senderId);

    SearchMessageResponse searchMessages(UUID roomId, String query, int limit, String lastKey);
}