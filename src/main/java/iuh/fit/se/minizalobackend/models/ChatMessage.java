package iuh.fit.se.minizalobackend.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String from;
    private String to;
    private String content;
    private LocalDateTime timestamp;
}
