package iuh.fit.se.minizalobackend.models;

import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.List;

@DynamoDbBean
@Getter
@Setter
public class MessageDynamo {

    private String chatRoomId;
    private String createdAt;
    private String messageId;
    private String senderId;
    private String content;
    private String type;
    private String senderName;
    private List<Attachment> attachments;
    private boolean isRead;
    private List<String> readBy; // List of user IDs for group chats
    private List<MessageReaction> reactions;
    private boolean isRecalled;
    private String recalledAt;
    private boolean isPinned;
    private String replyToMessageId;

    @DynamoDbPartitionKey
    public String getChatRoomId() {
        return chatRoomId;
    }

    @DynamoDbSortKey
    public String getCreatedAt() {
        return createdAt;
    }
}
