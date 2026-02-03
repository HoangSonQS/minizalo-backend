package iuh.fit.se.minizalobackend.dtos.response;

import iuh.fit.se.minizalobackend.models.ERoomEventType;
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
public class GroupEventResponse {
    private UUID id;
    private UUID groupId;
    private UUID userId;
    private String userName;
    private String userAvatar;
    private ERoomEventType eventType;
    private String metadata;
    private LocalDateTime createdAt;
}
