package iuh.fit.se.minizalobackend.dtos.request;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Data
public class MuteConversationRequest {
    @NotNull(message = "Room ID is required")
    private UUID roomId;

    @NotNull(message = "Mute status is required")
    private boolean mute;

    private Long durationMinutes; // null or 0 for indefinitely
}
