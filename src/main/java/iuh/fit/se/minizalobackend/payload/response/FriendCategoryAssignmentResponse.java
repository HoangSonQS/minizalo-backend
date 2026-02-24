package iuh.fit.se.minizalobackend.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class FriendCategoryAssignmentResponse {
    private UUID targetUserId;
    private UUID categoryId; // null => chưa gán
}

