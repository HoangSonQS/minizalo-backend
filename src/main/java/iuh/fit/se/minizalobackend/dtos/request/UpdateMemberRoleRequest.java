package iuh.fit.se.minizalobackend.dtos.request;

import iuh.fit.se.minizalobackend.models.ERoomRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMemberRoleRequest {
    @NotNull(message = "New role cannot be null")
    private ERoomRole role;
}
