package iuh.fit.se.minizalobackend.payload.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecallMessageRequest {
    private String roomId;
    private String messageId;
}
