package iuh.fit.se.minizalobackend.dtos.request;

import iuh.fit.se.minizalobackend.models.ECallType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateCallRequest {
    private UUID conversationId;
    private UUID receiverId;
    private ECallType callType;
}
