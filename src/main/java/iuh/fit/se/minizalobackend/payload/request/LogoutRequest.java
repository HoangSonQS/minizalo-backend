package iuh.fit.se.minizalobackend.payload.request;

import lombok.Data;

/**
 * Optional refreshToken:
 * - If provided: logout only that session (device).
 * - If omitted: logout all sessions for current user.
 */
@Data
public class LogoutRequest {
    private String refreshToken;
}

