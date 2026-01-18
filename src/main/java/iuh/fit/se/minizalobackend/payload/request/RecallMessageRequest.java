package iuh.fit.se.minizalobackend.payload.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecallMessageRequest {
    private String fromUserId; // ignored, but kept for potential structure compatibility
    private String toUserId; // ignored
    private String messageId;
}
