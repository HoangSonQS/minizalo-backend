package iuh.fit.se.minizalobackend.repository.impl;

import iuh.fit.se.minizalobackend.models.ChatSummary;
import iuh.fit.se.minizalobackend.repository.ChatSummaryRepository;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ChatSummaryRepositoryImpl implements ChatSummaryRepository {
    private final DynamoDbTable<ChatSummary> table;

    public ChatSummaryRepositoryImpl(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("ChatSummary", TableSchema.fromBean(ChatSummary.class));
    }

    @Override
    public void save(ChatSummary summary) {
        table.putItem(summary);
    }

    @Override
    public List<ChatSummary> getSummariesByRoomId(String roomId) {
        return table.query(r -> r.queryConditional(
                QueryConditional.keyEqualTo(k -> k.partitionValue(roomId))
        )).items().stream().collect(Collectors.toList());
    }
}
