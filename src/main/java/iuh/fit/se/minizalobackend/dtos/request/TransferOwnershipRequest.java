package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class TransferOwnershipRequest {
    @NotNull(message = "Group ID is required")
    private UUID groupId;

    @NotNull(message = "New owner ID is required")
    private UUID newOwnerId;
}
