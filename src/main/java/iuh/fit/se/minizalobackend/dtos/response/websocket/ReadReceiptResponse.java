package iuh.fit.se.minizalobackend.dtos.response.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReadReceiptResponse {
    private UUID groupId;
    private UUID userId;
    private LocalDateTime lastReadAt;
}
