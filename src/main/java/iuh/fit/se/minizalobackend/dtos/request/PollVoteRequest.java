package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PollVoteRequest {
    @NotNull(message = "Poll ID is required")
    private UUID pollId;

    @NotNull(message = "Option IDs list cannot be null")
    private List<UUID> optionIds;
}
