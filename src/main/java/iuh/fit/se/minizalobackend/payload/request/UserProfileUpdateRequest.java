package iuh.fit.se.minizalobackend.payload.request;

import lombok.Data;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Data
public class UserProfileUpdateRequest {
    @Size(max = 50)
    private String displayName;
    @Size(max = 255)
    private String statusMessage;
    @Size(max = 20)
    private String phone;
    @Size(max = 20)
    private String gender;
    private LocalDate dateOfBirth;
    @Size(max = 500)
    private String businessDescription;
}
