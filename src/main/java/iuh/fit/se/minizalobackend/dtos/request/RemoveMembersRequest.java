package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RemoveMembersRequest {
    @NotNull
    private UUID groupId;
    @NotEmpty
    private List<UUID> memberIds;
}
