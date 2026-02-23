package iuh.fit.se.minizalobackend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.net.URI;

@Slf4j
@Configuration
public class DynamoDBConfig {

    @Value("${aws.dynamodb.endpoint}")
    private String dynamodbEndpoint;

    @Value("${aws.accessKeyId}")
    private String accessKey;

    @Value("${aws.secretKey}")
    private String secretKey;

    @Value("${aws.region}")
    private String region;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(dynamodbEndpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    /** Tự động tạo bảng DynamoDB 'messages' khi khởi động (cần thiết cho -inMemory mode) */
    @Bean
    public CommandLineRunner initDynamoDBTables(DynamoDbClient dynamoDbClient) {
        return args -> {
            try {
                dynamoDbClient.createTable(r -> r
                        .tableName("messages")
                        .keySchema(
                                KeySchemaElement.builder()
                                        .attributeName("chatRoomId")
                                        .keyType(KeyType.HASH)
                                        .build(),
                                KeySchemaElement.builder()
                                        .attributeName("createdAt")
                                        .keyType(KeyType.RANGE)
                                        .build()
                        )
                        .attributeDefinitions(
                                AttributeDefinition.builder()
                                        .attributeName("chatRoomId")
                                        .attributeType(ScalarAttributeType.S)
                                        .build(),
                                AttributeDefinition.builder()
                                        .attributeName("createdAt")
                                        .attributeType(ScalarAttributeType.S)
                                        .build()
                        )
                        .billingMode(BillingMode.PAY_PER_REQUEST)
                );
                log.info("✅ DynamoDB table 'messages' created successfully.");
            } catch (ResourceInUseException e) {
                log.info("ℹ️ DynamoDB table 'messages' already exists.");
            } catch (Exception e) {
                log.error("❌ Failed to create DynamoDB table 'messages': {}", e.getMessage());
            }
        };
    }
}
