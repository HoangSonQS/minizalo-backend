package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class GroupCreationRequest {
    @NotBlank(message = "Group name cannot be blank")
    @Size(min = 3, max = 100, message = "Group name must be between 3 and 100 characters")
    private String name;

    @NotEmpty(message = "Group must have at least one member")
    private List<UUID> memberIds;
}
