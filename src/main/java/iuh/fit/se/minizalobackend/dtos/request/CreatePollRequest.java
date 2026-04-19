package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePollRequest {
    @NotNull(message = "Room/Group ID is required")
    private UUID roomId;

    @NotBlank(message = "Question is required")
    private String question;

    @NotEmpty(message = "At least one option is required")
    private List<String> options;

    private boolean allowMultipleChoices = false;
    private boolean allowAddOptions = false;
}
