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
public class StoryDynamo {
    private String userId;
    private String createdAt;
    private String storyId;
    private String mediaUrl;
    private String mediaType; // IMAGE, VIDEO, TEXT
    private String storyType; // STATUS, PHOTO, VIDEO, LOOP
    private String caption;
    private String privacy; // ALL_FRIENDS, SPECIFIC, EXCLUDE
    private List<String> permittedUserIds;
    private Long expiresAt; // TTL timestamp (seconds)
    private List<String> viewers;
    private List<String> reactions; // Simplified: List of "userId:type"
    private String backgroundConfig; // JSON for background colors/gradients for Status


    @DynamoDbPartitionKey
    public String getUserId() {
        return userId;
    }

    @DynamoDbSortKey
    public String getCreatedAt() {
        return createdAt;
    }
}
