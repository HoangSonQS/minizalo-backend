package iuh.fit.se.minizalobackend.payload.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LockAccountRequest {
    @NotBlank
    private String password;
}

