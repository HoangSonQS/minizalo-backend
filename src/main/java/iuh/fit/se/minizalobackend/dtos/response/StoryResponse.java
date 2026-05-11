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
public class StoryResponse {
    private String storyId;
    private String userId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String mediaUrl;
    private String mediaType;
    private String storyType;
    private String caption;
    private String privacy;
    private List<String> permittedUserIds;
    private String createdAt;
    private Long expiresAt;
    private List<String> viewers;
    private List<String> reactions;
    private String backgroundConfig;
}
