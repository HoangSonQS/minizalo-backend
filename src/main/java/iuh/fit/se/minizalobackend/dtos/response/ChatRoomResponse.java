package iuh.fit.se.minizalobackend.dtos.response;

import iuh.fit.se.minizalobackend.models.ERoomType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
public class ChatRoomResponse {
    private UUID id;
    private ERoomType type;
    private String name;
    private String avatarUrl;
    private UserResponse createdBy;
    private LocalDateTime createdAt;
    private iuh.fit.se.minizalobackend.models.MessageDynamo lastMessage;
    private int unreadCount;
    private List<RoomMemberResponse> members;
}
