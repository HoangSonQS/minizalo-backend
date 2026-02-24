package iuh.fit.se.minizalobackend.payload.request;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Data
public class FriendRequest {
    // UUID không phải CharSequence, dùng @NotNull thay vì @NotBlank
    @NotNull
    private UUID friendId;
}
