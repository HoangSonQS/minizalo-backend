package iuh.fit.se.minizalobackend.repository.impl;

import iuh.fit.se.minizalobackend.models.StoryDynamo;
import iuh.fit.se.minizalobackend.repository.StoryRepository;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class StoryRepositoryImpl implements StoryRepository {
    private final DynamoDbTable<StoryDynamo> storyTable;

    public StoryRepositoryImpl(DynamoDbEnhancedClient enhancedClient) {
        this.storyTable = enhancedClient.table("stories", TableSchema.fromBean(StoryDynamo.class));
    }

    @Override
    public void save(StoryDynamo story) {
        storyTable.putItem(story);
    }

    @Override
    public Optional<StoryDynamo> getStory(String userId, String createdAt) {
        Key key = Key.builder().partitionValue(userId).sortValue(createdAt).build();
        return Optional.ofNullable(storyTable.getItem(key));
    }

    @Override
    public List<StoryDynamo> getStoriesByUserId(String userId) {
        try {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build());
            List<StoryDynamo> results = new ArrayList<>();
            storyTable.query(r -> r.queryConditional(queryConditional).scanIndexForward(false)).items().forEach(results::add);
            return results;
        } catch (Exception e) {
            log.error("Error querying stories for user {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void delete(String userId, String createdAt) {
        Key key = Key.builder().partitionValue(userId).sortValue(createdAt).build();
        storyTable.deleteItem(key);
    }

    @Override
    public List<StoryDynamo> getAllActiveStories(List<String> userIds) {
        // In a real high-scale app, we might use BatchGetItem or GSI.
        // For simplicity, we query per user and filter by TTL manually if needed (though DynamoDB TTL handles deletion).
        List<StoryDynamo> allStories = new ArrayList<>();
        for (String userId : userIds) {
            allStories.addAll(getStoriesByUserId(userId));
        }
        return allStories;
    }

    @Override
    public void updatePrivacy(String userId, String createdAt, String privacy, List<String> permittedUserIds) {
        Optional<StoryDynamo> opt = getStory(userId, createdAt);
        if (opt.isPresent()) {
            StoryDynamo story = opt.get();
            story.setPrivacy(privacy);
            story.setPermittedUserIds(permittedUserIds);
            save(story);
        }
    }
}
