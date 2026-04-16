package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddPollOptionRequest {
    @NotNull(message = "Poll ID is required")
    private UUID pollId;

    @NotBlank(message = "Option text is required")
    private String text;
}
