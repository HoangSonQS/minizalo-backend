package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.StoryDynamo;
import java.util.List;
import java.util.Optional;

public interface StoryRepository {
    void save(StoryDynamo story);
    Optional<StoryDynamo> getStory(String userId, String createdAt);
    List<StoryDynamo> getStoriesByUserId(String userId);
    void delete(String userId, String createdAt);
    List<StoryDynamo> getAllActiveStories(List<String> userIds);
    void updatePrivacy(String userId, String createdAt, String privacy, List<String> permittedUserIds);
}
