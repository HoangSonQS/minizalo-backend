package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.Message;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class MessageRepository {

    private final DynamoDbTable<Message> messageTable;

    public MessageRepository(DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.messageTable = dynamoDbEnhancedClient.table("Messages", TableSchema.fromBean(Message.class));
    }

    public void save(Message message) {
        messageTable.putItem(message);
    }

    public Message findById(String conversationId, String messageId) {
        Key key = Key.builder().partitionValue(conversationId).sortValue(messageId).build();
        return messageTable.getItem(key);
    }

    public List<Message> findByConversationId(String conversationId) {
        return messageTable.query(r -> r.queryConditional(
                software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional.keyEqualTo(
                        k -> k.partitionValue(conversationId)))).items().stream().collect(Collectors.toList());
    }

    public void delete(Message message) {
        messageTable.deleteItem(message);
    }
}
