package iuh.fit.se.minizalobackend.dtos.response;

import iuh.fit.se.minizalobackend.models.ERoomRole;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class RoomMemberResponse {
    private UUID id;
    private UserResponse user;
    private ERoomRole role;
    private LocalDateTime joinedAt;
    private LocalDateTime lastReadAt;
}
