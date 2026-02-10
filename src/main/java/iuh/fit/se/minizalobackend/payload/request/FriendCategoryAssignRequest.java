package iuh.fit.se.minizalobackend.payload.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class FriendCategoryAssignRequest {
    @NotNull
    private UUID targetUserId;

    // null => hủy phân loại
    private UUID categoryId;
}

