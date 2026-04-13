package iuh.fit.se.minizalobackend.payload.request;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Data
public class FriendRequest {
    // UUID không phải CharSequence, dùng @NotNull thay vì @NotBlank
    @NotNull
    private UUID friendId;

    @Size(max = 150)
    private String inviteMessage;

    /** CHAT_WINDOW | PHONE_SEARCH | … */
    @Size(max = 32)
    private String inviteSource;

    private Boolean hideMyTimelineFromFriend;
}
