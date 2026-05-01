package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiSummarizeRequest {
    @NotBlank(message = "Start time cannot be blank")
    private String startTime;
    
    @NotBlank(message = "End time cannot be blank")
    private String endTime;

    private boolean isUnreadOnly;
}
