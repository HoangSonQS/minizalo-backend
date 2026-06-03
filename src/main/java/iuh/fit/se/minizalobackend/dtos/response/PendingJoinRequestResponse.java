package iuh.fit.se.minizalobackend.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingJoinRequestResponse {
    private String userId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String invitedByUserId;
    private String invitedByDisplayName;
    private LocalDateTime createdAt;
}
