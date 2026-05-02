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
public class GroupCallSessionResponse {
    private String token;
    private String appId;
    private String channelName;
    private String callSessionId;
    private long expireAt;
    private ECallType callType;

    private UUID hostId;
    private UUID conversationId;
    private List<ParticipantDto> participants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantDto {
        private UUID userId;
        private String status;
    }
}

