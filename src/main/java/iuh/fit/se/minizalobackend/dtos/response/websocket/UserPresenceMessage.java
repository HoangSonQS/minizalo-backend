package iuh.fit.se.minizalobackend.dtos.response.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPresenceMessage {
    private UUID userId;
    private boolean isOnline;
    private LocalDateTime lastSeen;
}
