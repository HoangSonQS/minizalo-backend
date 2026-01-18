package iuh.fit.se.minizalobackend.payload.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatMessageRequest {
    @NotBlank
    private String receiverId;

    @NotBlank
    private String content;
}
