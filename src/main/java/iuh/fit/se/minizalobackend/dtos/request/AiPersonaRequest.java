package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiPersonaRequest {
    @NotBlank(message = "Persona cannot be empty")
    private String persona;

    @NotBlank(message = "Question cannot be empty")
    private String question;
}
