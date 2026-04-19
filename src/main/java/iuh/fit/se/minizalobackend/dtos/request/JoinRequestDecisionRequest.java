package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class JoinRequestDecisionRequest {
    @NotNull
    private UUID groupId;
    @NotNull
    private UUID userId;
}
