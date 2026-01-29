package iuh.fit.se.minizalobackend.payload.response;

import iuh.fit.se.minizalobackend.models.EFriendStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendResponse {
    private UUID id;
    private UserProfileResponse user;
    private UserProfileResponse friend;
    private EFriendStatus status;
    private LocalDateTime createdAt;
}
