package iuh.fit.se.minizalobackend.payload.request;

import lombok.Data;

import jakarta.validation.constraints.Size;

@Data
public class UserProfileUpdateRequest {
    @Size(max = 50)
    private String displayName;
    @Size(max = 255)
    private String statusMessage;
}
