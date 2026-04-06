package iuh.fit.se.minizalobackend.payload.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String password;

    /**
     * WEB or MOBILE. Used to enforce session limits.
     * If missing, defaults to WEB (backward compatible).
     */
    private String deviceType;

    /**
     * Stable per-install/per-browser-profile id.
     * If missing, treated as "unknown".
     */
    private String deviceId;

    // Backward-compatible constructor for existing tests/callers
    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
        this.deviceType = "WEB";
        this.deviceId = "unknown";
    }
}
