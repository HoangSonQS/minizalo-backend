package iuh.fit.se.minizalobackend.dtos.response;

import iuh.fit.se.minizalobackend.models.ECallType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CallTokenResponse {
    private String token;
    private String appId;
    private String channelName;
    private String uid;
    private long expireAt;
    private ECallType callType;
}
