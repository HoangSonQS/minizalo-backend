package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import java.util.List;

public interface MessageDynamoRepository {
    void save(MessageDynamo message);

    void deleteAllByRoomId(String chatRoomId);

    PaginatedMessageResult getMessagesByRoomId(String chatRoomId, String lastEvaluatedKey, int limit);
    
    // Thêm hàm lấy tin nhắn trong khoảng thời gian phục vụ AI Summarize
    List<MessageDynamo> getMessagesBetweenDates(String chatRoomId, String startTime, String endTime);

    java.util.Optional<MessageDynamo> getMessage(String chatRoomId, String messageId);

    PaginatedMessageResult getPinnedMessagesByRoomId(String chatRoomId, String lastEvaluatedKey, int limit);

    long countPinnedMessages(String chatRoomId);
    long countMessagesBySender(String chatRoomId, String senderId);

    iuh.fit.se.minizalobackend.dtos.response.SearchMessageResponse searchMessages(String chatRoomId, String query,
            int limit, String lastEvaluatedKey, String senderId, String fromDateInclusive, String toDateInclusive);
}