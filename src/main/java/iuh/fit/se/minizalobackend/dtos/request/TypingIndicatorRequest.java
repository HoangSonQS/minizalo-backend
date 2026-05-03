package iuh.fit.se.minizalobackend.dtos.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingIndicatorRequest {
    private String roomId;
    
    @JsonProperty("isTyping")
    private boolean isTyping;

    @JsonProperty("isTyping")
    public boolean isTyping() {
        return isTyping;
    }
}
