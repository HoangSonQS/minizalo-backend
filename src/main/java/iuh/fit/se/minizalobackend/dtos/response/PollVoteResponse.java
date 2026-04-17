package iuh.fit.se.minizalobackend.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PollVoteResponse {
    private String id;
    private String userId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private LocalDateTime votedAt;
}
