package iuh.fit.se.minizalobackend.dtos.response;

import iuh.fit.se.minizalobackend.models.ECallType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupCallEventPayload {
    private String eventType;
    private String callSessionId;
    private String channelName;
    private UUID conversationId;
    private UUID hostId;
    private ECallType callType;

    /** actor = người tạo event (join/leave/end/decline). */
    private UUID actorId;
    private String actorStatus;

    private List<GroupCallSessionResponse.ParticipantDto> participants;
}

