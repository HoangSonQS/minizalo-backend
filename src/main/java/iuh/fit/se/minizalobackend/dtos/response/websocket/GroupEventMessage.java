package iuh.fit.se.minizalobackend.dtos.response.websocket;

import iuh.fit.se.minizalobackend.models.ERoomEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupEventMessage {
    private String eventId;
    private String groupId;
    private ERoomEventType eventType; // MEMBER_ADDED, MEMBER_REMOVED, GROUP_UPDATED, GROUP_CREATED
    private String message;
    private String affectedUserId;
    private String affectedUsername;
    private String timestamp;
}
