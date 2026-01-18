package iuh.fit.se.minizalobackend.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Message {

    private String conversationId;
    private String messageId;
    private String fromUserId;
    private String toUserId;
    private String content;
    private LocalDateTime timestamp;
    private boolean recalled;

    @DynamoDbPartitionKey
    public String getConversationId() {
        return conversationId;
    }

    @DynamoDbSortKey
    public String getMessageId() {
        return messageId;
    }
}
