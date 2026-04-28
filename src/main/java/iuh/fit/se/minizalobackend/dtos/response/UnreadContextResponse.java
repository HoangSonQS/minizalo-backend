package iuh.fit.se.minizalobackend.dtos.response;

import iuh.fit.se.minizalobackend.models.MessageDynamo;
import java.util.List;

/**
 * Response cho API lấy context xung quanh tin nhắn chưa đọc cũ nhất.
 * Chứa các tin nhắn trước target (cũ hơn) + target + các tin nhắn sau (mới hơn).
 */
public class UnreadContextResponse {
    /** Tin nhắn chưa đọc cũ nhất (đích cần scroll tới) */
    private MessageDynamo targetMessage;

    /** Các tin nhắn SAU target (mới hơn) – đây là các tin nhắn hiển thị phía trên trong inverted list */
    private List<MessageDynamo> messagesAfter;

    /** Các tin nhắn TRƯỚC target (cũ hơn) */
    private List<MessageDynamo> messagesBefore;

    /** Còn tin nhắn cũ hơn không (để render nút load more) */
    private boolean hasMoreBefore;

    /** Còn tin nhắn mới hơn không */
    private boolean hasMoreAfter;

    /** Index của targetMessage trong mảng combined (messages = messagesAfter + target + messagesBefore) */
    private int targetIndexInList;

    public UnreadContextResponse() {}

    public UnreadContextResponse(MessageDynamo targetMessage, List<MessageDynamo> messagesAfter,
            List<MessageDynamo> messagesBefore, boolean hasMoreBefore, boolean hasMoreAfter) {
        this.targetMessage = targetMessage;
        this.messagesAfter = messagesAfter;
        this.messagesBefore = messagesBefore;
        this.hasMoreBefore = hasMoreBefore;
        this.hasMoreAfter = hasMoreAfter;
        // Trong inverted FlatList: index 0 = mới nhất
        // combined = [messagesAfter (mới nhất đầu tiên), target, messagesBefore (cũ nhất)]
        this.targetIndexInList = messagesAfter.size();
    }

    public MessageDynamo getTargetMessage() { return targetMessage; }
    public void setTargetMessage(MessageDynamo targetMessage) { this.targetMessage = targetMessage; }

    public List<MessageDynamo> getMessagesAfter() { return messagesAfter; }
    public void setMessagesAfter(List<MessageDynamo> messagesAfter) { this.messagesAfter = messagesAfter; }

    public List<MessageDynamo> getMessagesBefore() { return messagesBefore; }
    public void setMessagesBefore(List<MessageDynamo> messagesBefore) { this.messagesBefore = messagesBefore; }

    public boolean isHasMoreBefore() { return hasMoreBefore; }
    public void setHasMoreBefore(boolean hasMoreBefore) { this.hasMoreBefore = hasMoreBefore; }

    public boolean isHasMoreAfter() { return hasMoreAfter; }
    public void setHasMoreAfter(boolean hasMoreAfter) { this.hasMoreAfter = hasMoreAfter; }

    public int getTargetIndexInList() { return targetIndexInList; }
    public void setTargetIndexInList(int targetIndexInList) { this.targetIndexInList = targetIndexInList; }
}
