package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForwardMessageRequest {
    @NotBlank(message = "Original message ID is required")
    private String originalMessageId;

    @NotBlank(message = "Original room ID is required")
    private String originalRoomId;

    @NotBlank(message = "Target room ID is required")
    private String targetRoomId;
}
