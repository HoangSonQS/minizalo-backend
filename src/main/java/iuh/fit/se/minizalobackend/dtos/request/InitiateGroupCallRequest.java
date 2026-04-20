package iuh.fit.se.minizalobackend.dtos.request;

import iuh.fit.se.minizalobackend.models.ECallType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateGroupCallRequest {
    private UUID conversationId;
    private List<UUID> receiverIds;
    private ECallType callType;
}

