package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class JoinRequestDecisionRequest {
    @NotNull(message = "Group ID is required")
    private UUID groupId;

    @NotNull(message = "User ID is required")
    private UUID userId;
}
