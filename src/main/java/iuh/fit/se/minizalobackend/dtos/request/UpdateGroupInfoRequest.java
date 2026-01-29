package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateGroupInfoRequest {
    @Size(min = 3, max = 100, message = "Group name must be between 3 and 100 characters")
    private String name;

    private String avatarUrl;
}
