package iuh.fit.se.minizalobackend.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class FriendCategoryResponse {
    private UUID id;
    private String name;
    private String color;
}

