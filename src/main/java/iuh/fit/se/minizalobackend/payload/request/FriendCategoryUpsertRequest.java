package iuh.fit.se.minizalobackend.payload.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FriendCategoryUpsertRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String color;
}

