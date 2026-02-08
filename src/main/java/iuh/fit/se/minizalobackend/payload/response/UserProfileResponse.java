package iuh.fit.se.minizalobackend.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private UUID id;
    private String username;
    private String email;
    private String displayName;
    private String avatarUrl;
    private String statusMessage;
    private String phone;
    private String gender;
    private LocalDate dateOfBirth;
    private String businessDescription;
    private LocalDateTime lastSeen;
    private Boolean isOnline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> roles;
}
