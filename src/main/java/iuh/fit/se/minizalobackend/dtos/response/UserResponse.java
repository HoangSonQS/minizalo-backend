package iuh.fit.se.minizalobackend.dtos.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
public class UserResponse {
    private UUID id;
    private String username;
    private String displayName;
    private String avatarUrl;
}
