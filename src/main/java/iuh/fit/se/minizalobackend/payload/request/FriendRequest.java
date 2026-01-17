package iuh.fit.se.minizalobackend.payload.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

@Data
public class FriendRequest {
    @NotBlank
    private UUID friendId;
}
