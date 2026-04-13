package iuh.fit.se.minizalobackend.dtos.response;

import iuh.fit.se.minizalobackend.models.ECallType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomingCallPayload {
    private String callSessionId;
    private String channelName;
    private ECallType callType;
    private CallerInfo caller;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallerInfo {
        private UUID id;
        private String name;
        private String avatar;
    }
}
