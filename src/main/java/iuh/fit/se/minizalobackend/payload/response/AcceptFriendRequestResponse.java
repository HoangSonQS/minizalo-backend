package iuh.fit.se.minizalobackend.payload.response;

import iuh.fit.se.minizalobackend.dtos.response.ChatRoomResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcceptFriendRequestResponse {
    private FriendResponse friendship;
    private ChatRoomResponse chatRoom;
}
