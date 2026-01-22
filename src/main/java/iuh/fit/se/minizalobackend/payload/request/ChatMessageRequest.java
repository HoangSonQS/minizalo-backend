package iuh.fit.se.minizalobackend.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatMessageRequest {
    @NotBlank
    private String receiverId;

    @NotBlank
    @Size(max = 1000, message = "Content must be less than 1000 characters")
    private String content;
}
