package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiTextRequest {
    @NotBlank(message = "Text cannot be empty")
    private String text;
    
    private String targetLanguage;
}
