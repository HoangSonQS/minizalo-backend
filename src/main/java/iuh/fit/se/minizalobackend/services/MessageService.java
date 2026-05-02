package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.dtos.response.SearchMessageResponse;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.payload.request.ChatMessageRequest;
import java.util.UUID;

public interface MessageService {
    MessageDynamo saveMessage(MessageDynamo message);

    void deleteAllMessages(String chatRoomId);

    PaginatedMessageResult getRoomMessages(UUID roomId, String lastKey, int limit);

    void recallMessage(String chatRoomId, String messageId);

    void recallMessage(String chatRoomId, String messageId, String requesterId);

    void markMessageAsRead(String chatRoomId, String messageId, String userId);

    void addReaction(String chatRoomId, String messageId, String userId, String emoji);

    void removeReaction(String chatRoomId, String messageId, String userId);

    void pinMessage(String chatRoomId, String messageId, boolean pin);

    void pinMessage(String chatRoomId, String messageId, boolean pin, String actorName, String messageType);

    PaginatedMessageResult getPinnedMessages(UUID roomId, String lastKey, int limit);

    MessageDynamo forwardMessage(String originalRoomId, String originalMessageId, String targetRoomId, String senderId);

    MessageDynamo processMessage(ChatMessageRequest request, String senderId);

    /**
     * Update nội dung (và optionally type) của 1 tin nhắn đã tồn tại, rồi broadcast event
     * MESSAGE_UPDATED qua WS `/topic/chat/{roomId}` để client patch in-place (không tạo bubble mới).
     * Dùng chính cho group call: tin STARTED → ENDED cập nhật duration + status tại chỗ.
     *
     * @return MessageDynamo đã cập nhật, hoặc null nếu không tìm thấy message.
     */
    MessageDynamo updateMessageContent(String chatRoomId, String messageId, String newContent, String newType);

    SearchMessageResponse searchMessages(UUID roomId, String query, int limit, String lastKey,
            String senderId, String fromDateInclusive, String toDateInclusive);

    /**
     * Tìm kiếm tin nhắn toàn cục trên tất cả các phòng mà userId là thành viên.
     * Kết quả được gộp lại và sắp xếp theo thời gian giảm dần.
     *
     * @param userId userId của người đang search
     * @param query  từ khóa tìm kiếm
     * @param limit  số kết quả tối đa
     * @return SearchMessageResponse chứa danh sách tin nhắn khớp
     */
    SearchMessageResponse searchMessagesGlobal(String userId, String query, int limit);

    /**
     * Lấy context xung quanh tin nhắn chưa đọc cũ nhất của user trong phòng.
     * Trả về messages xung quanh target để FlatList render và scroll chính xác.
     */
    iuh.fit.se.minizalobackend.dtos.response.UnreadContextResponse getUnreadContext(
            UUID roomId, String userId, int countBefore, int countAfter);
}