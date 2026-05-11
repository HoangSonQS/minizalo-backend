package iuh.fit.se.minizalobackend.config;

import iuh.fit.se.minizalobackend.models.ChatSummary;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveSpecification;
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveRequest;

@Component
@RequiredArgsConstructor
@Slf4j
@org.springframework.context.annotation.Profile("!test")
public class DynamoDbTableInitializer {

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbClient dynamoDbClient;

    @PostConstruct
    public void init() {
        int maxRetries = 5;
        int retryDelayMs = 3000;

        for (int i = 0; i < maxRetries; i++) {
            try {
                log.info("Attempting to connect to DynamoDB and create table: messages (Attempt {}/{})", i + 1,
                        maxRetries);
                createTable(MessageDynamo.class, "messages", null);
                createTable(ChatSummary.class, "ChatSummary", "ttl");
                createTable(iuh.fit.se.minizalobackend.models.StoryDynamo.class, "stories", "expiresAt");
                log.info("DynamoDB table initialization completed successfully.");
                return;
            } catch (Exception e) {
                log.error("Failed to initialize DynamoDB table (Attempt {}/{}): {}", i + 1, maxRetries, e.getMessage());
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("Gave up creating DynamoDB table after {} attempts.", maxRetries);
    }

    private <T> void createTable(Class<T> beanClass, String tableName, String ttlAttribute) {
        DynamoDbTable<T> table = enhancedClient.table(tableName, TableSchema.fromBean(beanClass));
        try {
            table.createTable();
            log.info("Successfully created DynamoDB table: {}", tableName);
            
            if (ttlAttribute != null) {
                enableTtl(tableName, ttlAttribute);
            }
        } catch (ResourceInUseException e) {
            log.info("DynamoDB table '{}' already exists. Skipping creation.", tableName);
            // Optionally ensure TTL is enabled even if table exists
            if (ttlAttribute != null) {
                enableTtl(tableName, ttlAttribute);
            }
        } catch (Exception e) {
            log.error("Error creating table {}: {}", tableName, e.getMessage());
            throw e;
        }
    }

    private void enableTtl(String tableName, String attributeName) {
        try {
            dynamoDbClient.updateTimeToLive(UpdateTimeToLiveRequest.builder()
                    .tableName(tableName)
                    .timeToLiveSpecification(TimeToLiveSpecification.builder()
                            .attributeName(attributeName)
                            .enabled(true)
                            .build())
                    .build());
            log.info("Enabled TTL on table '{}' using attribute '{}'", tableName, attributeName);
        } catch (Exception e) {
            // updateTimeToLive might fail if it's already enabled or still creating
            log.warn("Could not update TTL on table '{}': {}", tableName, e.getMessage());
        }
    }
}
