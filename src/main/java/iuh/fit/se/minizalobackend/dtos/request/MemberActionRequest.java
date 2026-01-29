package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class MemberActionRequest {
    @NotEmpty(message = "Member IDs cannot be empty")
    private List<UUID> userIds;
}
