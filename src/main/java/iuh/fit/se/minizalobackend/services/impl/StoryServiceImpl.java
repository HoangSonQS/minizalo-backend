package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.dtos.response.StoryResponse;
import iuh.fit.se.minizalobackend.models.EFriendStatus;
import iuh.fit.se.minizalobackend.models.Friend;
import iuh.fit.se.minizalobackend.models.StoryDynamo;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.repository.FriendRepository;
import iuh.fit.se.minizalobackend.repository.StoryRepository;
import iuh.fit.se.minizalobackend.services.MinioService;
import iuh.fit.se.minizalobackend.services.StoryService;
import iuh.fit.se.minizalobackend.services.NotificationService;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryServiceImpl implements StoryService {

    private final StoryRepository storyRepository;
    private final MinioService minioService;
    private final FriendRepository friendRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Override
    public StoryResponse createStory(User user, MultipartFile file, String caption, String storyType, String privacy, List<String> permittedUserIds, String backgroundConfig) throws IOException {
        try {
            String fileUrl = null;
            String mediaType = "TEXT";
            
            if (file != null && !file.isEmpty()) {
                String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                fileUrl = minioService.uploadFile(file, "stories/", fileName);
                mediaType = (file.getContentType() != null && file.getContentType().startsWith("video")) ? "VIDEO" : "IMAGE";
            } else if ("STATUS".equals(storyType)) {
                mediaType = "TEXT";
            }

            Instant now = Instant.now();
            StoryDynamo story = new StoryDynamo();
            story.setStoryId(UUID.randomUUID().toString());
            story.setUserId(user.getId().toString());
            story.setCreatedAt(now.toString());
            story.setMediaUrl(fileUrl);
            story.setMediaType(mediaType);
            story.setStoryType(storyType != null ? storyType : "PHOTO");
            story.setCaption(caption);
            story.setPrivacy(privacy != null ? privacy : "ALL_FRIENDS");
            story.setPermittedUserIds(permittedUserIds != null ? permittedUserIds : new ArrayList<>());
            story.setExpiresAt(now.getEpochSecond() + 86400); // 24 hours
            story.setViewers(new ArrayList<>());
            story.setReactions(new ArrayList<>());
            story.setBackgroundConfig(backgroundConfig);

            storyRepository.save(story);

            return mapToResponse(story, user);
        } catch (Exception e) {
            log.error("Error creating story for user {}: {}", user.getUsername(), e.getMessage());
            throw e;
        }
    }

    @Override
    public List<StoryResponse> getFeed(User user) {
        try {
            String currentUserId = user.getId().toString();
            // Get friends
            List<Friend> friendsAsUser = friendRepository.findByUserAndStatus(user, EFriendStatus.ACCEPTED);
            List<Friend> friendsAsFriend = friendRepository.findByFriendAndStatus(user, EFriendStatus.ACCEPTED);

            java.util.Set<String> friendIds = new java.util.HashSet<>();
            friendsAsUser.forEach(f -> friendIds.add(f.getFriend().getId().toString()));
            friendsAsFriend.forEach(f -> friendIds.add(f.getUser().getId().toString()));
            
            // Include self
            friendIds.add(currentUserId);

            List<StoryDynamo> allStories = storyRepository.getAllActiveStories(new ArrayList<>(friendIds));
            
            // Filter by privacy
            List<StoryDynamo> filteredStories = allStories.stream()
                .filter(s -> {
                    // Own stories are always visible
                    if (s.getUserId().equals(currentUserId)) return true;
                    
                    String privacy = s.getPrivacy() != null ? s.getPrivacy() : "ALL_FRIENDS";
                    List<String> permitted = s.getPermittedUserIds() != null ? s.getPermittedUserIds() : new ArrayList<>();
                    
                    if ("ALL_FRIENDS".equals(privacy)) return true;
                    if ("SPECIFIC".equals(privacy)) return permitted.contains(currentUserId);
                    if ("EXCLUDE".equals(privacy)) return !permitted.contains(currentUserId);
                    
                    return true;
                })
                .collect(Collectors.toList());

            // Fetch all users info
            List<User> users = userRepository.findAllById(friendIds.stream().map(UUID::fromString).collect(Collectors.toList()));
            java.util.Map<String, User> userMap = users.stream().collect(Collectors.toMap(
                u -> u.getId().toString(), 
                u -> u,
                (existing, replacement) -> existing
            ));

            return filteredStories.stream()
                    .map(s -> {
                        User u = userMap.get(s.getUserId());
                        return mapToResponse(s, u);
                    })
                    .sorted((a, b) -> {
                        if (a.getCreatedAt() == null) return 1;
                        if (b.getCreatedAt() == null) return -1;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error in getFeed for user {}: {}", user.getUsername(), e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<StoryResponse> getMyStories(User user) {
        return storyRepository.getStoriesByUserId(user.getId().toString()).stream()
                .map(s -> mapToResponse(s, user))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteStory(User user, String createdAt) {
        storyRepository.delete(user.getId().toString(), createdAt);
    }

    @Override
    public void updatePrivacy(User user, String createdAt, String privacy, List<String> permittedUserIds) {
        storyRepository.updatePrivacy(user.getId().toString(), createdAt, privacy, permittedUserIds);
    }

    @Override
    public void viewStory(User user, String userId, String createdAt) {
        storyRepository.getStory(userId, createdAt).ifPresent(story -> {
            // Do not count owner's view
            if (story.getUserId().equals(user.getId().toString())) return;

            if (story.getViewers() == null) story.setViewers(new ArrayList<>());
            if (!story.getViewers().contains(user.getId().toString())) {
                story.getViewers().add(user.getId().toString());
                storyRepository.save(story);
            }
        });
    }

    @Override
    public void addReaction(User user, String userId, String createdAt, String type) {
        storyRepository.getStory(userId, createdAt).ifPresent(story -> {
            if (story.getReactions() == null) story.setReactions(new ArrayList<>());
            String entry = user.getId().toString() + ":" + type;
            // Remove existing reaction from this user if any
            story.getReactions().removeIf(r -> r.startsWith(user.getId().toString() + ":"));
            story.getReactions().add(entry);
            storyRepository.save(story);

            // Send notification to story owner
            if (!user.getId().toString().equals(userId)) {
                userRepository.findById(UUID.fromString(userId)).ifPresent(owner -> {
                    if (owner.getFcmToken() != null) {
                        String emoji = "heart".equals(type) ? "❤️" : "haha".equals(type) ? "😆" : "wow".equals(type) ? "😮" : "sad".equals(type) ? "😢" : "angry".equals(type) ? "😡" : "👍";
                        String title = "Story";
                        String body = user.getDisplayName() + " đã thả " + emoji + " vào Story của bạn";
                        notificationService.sendStoryNotification(owner.getId(), owner.getFcmToken(), title, body, userId, createdAt, user.getDisplayName());
                    }
                });
            }
        });
    }

    private StoryResponse mapToResponse(StoryDynamo story, User user) {
        return StoryResponse.builder()
                .storyId(story.getStoryId())
                .userId(story.getUserId())
                .mediaUrl(minioService.ensurePublicUrl(story.getMediaUrl()))
                .mediaType(story.getMediaType())
                .storyType(story.getStoryType())
                .caption(story.getCaption())
                .privacy(story.getPrivacy())
                .permittedUserIds(story.getPermittedUserIds())
                .createdAt(story.getCreatedAt())
                .expiresAt(story.getExpiresAt())
                .viewers(story.getViewers())
                .reactions(story.getReactions())
                .backgroundConfig(story.getBackgroundConfig())
                .displayName(user != null ? user.getDisplayName() : null)
                .avatarUrl(user != null ? minioService.ensurePublicUrl(user.getAvatarUrl()) : null)
                .username(user != null ? user.getUsername() : null)
                .build();
    }
}
