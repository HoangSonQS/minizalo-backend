package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class BlockMemberRequest {
    @NotNull(message = "Group ID is required")
    private UUID groupId;

    @NotNull(message = "Target user ID is required")
    private UUID targetUserId;
}
