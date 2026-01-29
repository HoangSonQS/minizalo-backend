package iuh.fit.se.minizalobackend.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private UUID id;
    private String senderId;
    private String receiverId;
    private String content;
    private LocalDateTime createdAt;
    private boolean recalled;
}
