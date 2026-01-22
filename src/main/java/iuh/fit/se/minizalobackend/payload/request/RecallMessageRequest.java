package iuh.fit.se.minizalobackend.payload.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecallMessageRequest {
    private String fromUserId;
    private String toUserId;
    private String messageId;
}
