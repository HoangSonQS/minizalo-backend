package iuh.fit.se.minizalobackend.dtos.response.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupChatMessage {
    private String messageId;
    private String groupId;
    private String senderId;
    private String senderUsername;
    private String content;
    private String timestamp;
    private boolean isRecalled;
}
