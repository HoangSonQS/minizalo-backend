package iuh.fit.se.minizalobackend.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GroupResponse {
    private String id;
    private String groupName;
    private String avatarUrl;
    private String wallpaperUrl;
    private String description;
    private String ownerId;
    private LocalDateTime createdAt;
    private List<GroupMemberResponse> members;
    private GroupSettingsResponse settings;
    private List<PendingJoinRequestResponse> pendingJoinRequests;
    private int pendingJoinRequestCount;

    public GroupResponse(
            String id,
            String groupName,
            String avatarUrl,
            String wallpaperUrl,
            String description,
            String ownerId,
            LocalDateTime createdAt,
            List<GroupMemberResponse> members,
            GroupSettingsResponse settings) {
        this.id = id;
        this.groupName = groupName;
        this.avatarUrl = avatarUrl;
        this.wallpaperUrl = wallpaperUrl;
        this.description = description;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
        this.members = members;
        this.settings = settings;
        this.pendingJoinRequests = List.of();
        this.pendingJoinRequestCount = 0;
    }
}
