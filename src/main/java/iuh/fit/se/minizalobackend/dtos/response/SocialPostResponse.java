package iuh.fit.se.minizalobackend.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialPostResponse {
    private String id;
    private String userId;
    private String displayName;
    private String username;
    private String avatarUrl;
    private String content;
    private String mediaUrl;
    private String mediaType;
    private String createdAt;
    private String privacy;
    private List<String> permittedUserIds;
    private List<MediaItem> mediaItems;
    private List<CommentItem> comments;
    private List<ReactionItem> reactions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaItem {
        private String id;
        private String mediaUrl;
        private String mediaType;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentItem {
        private String id;
        private String userId;
        private String displayName;
        private String username;
        private String avatarUrl;
        private String content;
        private String createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactionItem {
        private String id;
        private String userId;
        private String displayName;
        private String username;
        private String avatarUrl;
        private String type;
        private String createdAt;
    }
}
