package iuh.fit.se.minizalobackend.payload.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import iuh.fit.se.minizalobackend.models.EPrivacyAudience;
import lombok.Data;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Data
public class UserProfileUpdateRequest {
    @Size(max = 50)
    private String displayName;
    @Size(max = 255)
    private String avatarUrl;
    @Size(max = 255)
    private String coverPhotoUrl;
    @Size(max = 255)
    private String statusMessage;
    @Size(max = 20)
    private String phone;
    @Size(max = 20)
    private String gender;
    private LocalDate dateOfBirth;
    @Size(max = 500)
    private String businessDescription;
    private Boolean allowPhoneSearch;

    @JsonAlias("allow_messages_from")
    private EPrivacyAudience allowMessagesFrom;

    @JsonAlias("allow_calls_from")
    private EPrivacyAudience allowCallsFrom;
}
