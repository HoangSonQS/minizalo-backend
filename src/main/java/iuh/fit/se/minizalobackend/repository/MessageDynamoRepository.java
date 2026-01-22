package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MessageDynamoRepository {

    private final DynamoDbTable<MessageDynamo> messageTable;

    public MessageDynamoRepository(DynamoDbEnhancedClient enhancedClient) {
        this.messageTable = enhancedClient.table("messages", TableSchema.fromBean(MessageDynamo.class));
    }

    public void save(MessageDynamo message) {
        messageTable.putItem(message);
    }

    public PaginatedMessageResult getMessagesByRoomId(String chatRoomId, String lastEvaluatedKey, int limit) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(chatRoomId).build());

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit)
                .scanIndexForward(false); // Sort by createdAt descending (newest first)

        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            Map<String, AttributeValue> startKey = deserializeExclusiveStartKey(lastEvaluatedKey);
            requestBuilder.exclusiveStartKey(startKey);
        }

        var pagedResult = messageTable.query(requestBuilder.build());
        Optional<Page<MessageDynamo>> firstPage = pagedResult.stream().findFirst();

        if (firstPage.isPresent()) {
            Page<MessageDynamo> page = firstPage.get();
            List<MessageDynamo> messages = page.items();
            String newLastEvaluatedKey = serializeExclusiveStartKey(page.lastEvaluatedKey());
            return new PaginatedMessageResult(messages, newLastEvaluatedKey);
        } else {
            return new PaginatedMessageResult(Collections.emptyList(), null);
        }
    }

    private String serializeExclusiveStartKey(Map<String, AttributeValue> key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        String chatRoomId = key.get("chatRoomId").s();
        String createdAt = key.get("createdAt").s();
        String combined = chatRoomId + ":" + createdAt;
        return Base64.getEncoder().encodeToString(combined.getBytes());
    }

    private Map<String, AttributeValue> deserializeExclusiveStartKey(String base64Key) {
        if (base64Key == null || base64Key.isEmpty()) {
            return null;
        }
        byte[] decodedBytes = Base64.getDecoder().decode(base64Key);
        String[] parts = new String(decodedBytes).split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid lastEvaluatedKey format");
        }

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("chatRoomId", AttributeValue.builder().s(parts[0]).build());
        key.put("createdAt", AttributeValue.builder().s(parts[1]).build());
        return key;
    }
}