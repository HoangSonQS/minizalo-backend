package iuh.fit.se.minizalobackend.dtos.request;

import lombok.Data;
import java.util.UUID;

@Data
public class EndCallRequest {
    private UUID callSessionId;
}
