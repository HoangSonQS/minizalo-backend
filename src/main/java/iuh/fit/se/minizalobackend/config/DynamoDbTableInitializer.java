package iuh.fit.se.minizalobackend.config;

import iuh.fit.se.minizalobackend.models.MessageDynamo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

@Component
@RequiredArgsConstructor
@Slf4j
public class DynamoDbTableInitializer {

    private final DynamoDbEnhancedClient enhancedClient;

    @PostConstruct
    public void init() {
        int maxRetries = 5;
        int retryDelayMs = 3000;

        for (int i = 0; i < maxRetries; i++) {
            try {
                log.info("Attempting to connect to DynamoDB and create table: messages (Attempt {}/{})", i + 1, maxRetries);
                createTable(MessageDynamo.class, "messages");
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

    private <T> void createTable(Class<T> beanClass, String tableName) {
        DynamoDbTable<T> table = enhancedClient.table(tableName, TableSchema.fromBean(beanClass));
        try {
            table.createTable();
            log.info("Successfully created/verified DynamoDB table: {}", tableName);
        } catch (ResourceInUseException e) {
            log.info("DynamoDB table '{}' already exists. Skipping creation.", tableName);
        }
    }
}
