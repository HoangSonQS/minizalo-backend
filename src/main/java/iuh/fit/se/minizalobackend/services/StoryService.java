package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.dtos.response.StoryResponse;
import iuh.fit.se.minizalobackend.models.User;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface StoryService {
    StoryResponse createStory(User user, MultipartFile file, String caption, String storyType, String privacy, List<String> permittedUserIds, String backgroundConfig) throws IOException;
    List<StoryResponse> getFeed(User user);
    List<StoryResponse> getMyStories(User user);
    void deleteStory(User user, String createdAt);
    void viewStory(User user, String userId, String createdAt);
    void addReaction(User user, String userId, String createdAt, String type);
    void updatePrivacy(User user, String createdAt, String privacy, List<String> permittedUserIds);
}
