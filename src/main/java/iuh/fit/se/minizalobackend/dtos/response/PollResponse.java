package iuh.fit.se.minizalobackend.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PollResponse {
    private String id;
    private String question;
    private boolean allowMultipleChoices;
    private boolean allowAddOptions;
    private boolean closed;
    private String createdById;
    private String createdByName;
    private String roomId;
    private List<PollOptionResponse> options;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
