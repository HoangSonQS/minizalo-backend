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
    private UserResponse user; // The user who sent/received the request
    private UserResponse friend; // The target friend
    private EFriendStatus status;
    private LocalDateTime createdAt;
}
