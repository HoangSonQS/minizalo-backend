package iuh.fit.se.minizalobackend.dtos.request;

import iuh.fit.se.minizalobackend.models.ECallType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CallTokenRequest {
    @NotNull(message = "Conversation ID is required")
    private UUID conversationId;

    @NotNull(message = "Call type is required")
    private ECallType callType;
}
