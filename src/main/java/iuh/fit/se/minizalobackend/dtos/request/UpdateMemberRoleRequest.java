package iuh.fit.se.minizalobackend.dtos.request;

import iuh.fit.se.minizalobackend.models.ERoomRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UpdateMemberRoleRequest {
    @NotNull(message = "Group ID cannot be null")
    private UUID groupId;

    @NotNull(message = "Target user ID cannot be null")
    private UUID targetUserId;

    @NotNull(message = "New role cannot be null")
    private ERoomRole role;
}
