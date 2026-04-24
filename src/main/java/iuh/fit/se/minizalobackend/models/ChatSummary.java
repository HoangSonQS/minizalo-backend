package iuh.fit.se.minizalobackend.models;

import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Getter
@Setter
public class ChatSummary {
    private String roomId;
    private String createdAt;
    private String summaryId;
    private String content;
    private long ttl; // Time To Live (epoch seconds)

    @DynamoDbPartitionKey
    public String getRoomId() {
        return roomId;
    }

    @DynamoDbSortKey
    public String getCreatedAt() {
        return createdAt;
    }
}
