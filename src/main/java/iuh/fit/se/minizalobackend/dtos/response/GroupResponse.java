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
    private String ownerId;
    private LocalDateTime createdAt;
    private List<GroupMemberResponse> members;
    private GroupSettingsResponse settings;
    private boolean disbanded;
    /** Chỉ điền khi người xem là trưởng/phó nhóm — danh sách chờ duyệt */
    private java.util.List<PendingJoinRequestResponse> pendingJoinRequests;
    private int pendingJoinRequestCount;
}
