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
    long countUnreadMessages(String chatRoomId, String userId, String lastReadAtIso);

    iuh.fit.se.minizalobackend.dtos.response.SearchMessageResponse searchMessages(String chatRoomId, String query,
            int limit, String lastEvaluatedKey, String senderId, String fromDateInclusive, String toDateInclusive);

    /**
     * Lấy các tin nhắn xung quanh một tin cụ thể (dùng cho tính năng scroll đến tin chưa đọc).
     * @param chatRoomId  ID phòng chat
     * @param targetCreatedAt  createdAt (sort key) của tin nhắn đích
     * @param countBefore  số tin cũ hơn target cần lấy
     * @param countAfter   số tin mới hơn target cần lấy
     * @return UnreadContextResult
     */
    UnreadContextResult getMessagesAroundTarget(String chatRoomId, String targetCreatedAt, int countBefore, int countAfter);

    /** Hoàn trả tin nhắn chưa đọc cũ nhất trong phòng của user. */
    java.util.Optional<iuh.fit.se.minizalobackend.models.MessageDynamo> getOldestUnreadMessage(String chatRoomId, String userId, String lastReadAtIso);

    record UnreadContextResult(
        java.util.List<iuh.fit.se.minizalobackend.models.MessageDynamo> messagesBefore,
        java.util.List<iuh.fit.se.minizalobackend.models.MessageDynamo> messagesAfter,
        boolean hasMoreBefore,
        boolean hasMoreAfter
    ) {}
}