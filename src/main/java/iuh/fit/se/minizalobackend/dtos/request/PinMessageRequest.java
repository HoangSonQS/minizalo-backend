package iuh.fit.se.minizalobackend.dtos.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PinMessageRequest {
    private String roomId;
    private String messageId;
    private boolean pin;
}
