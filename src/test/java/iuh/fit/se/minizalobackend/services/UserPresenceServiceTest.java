package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.services.impl.UserPresenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserPresenceServiceTest {

    private UserPresenceService userPresenceService;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userPresenceService = new UserPresenceServiceImpl();
    }

    @Test
    void markUserOnline_ShouldReturnTrue() {
        userPresenceService.markUserOnline(userId);
        assertTrue(userPresenceService.isUserOnline(userId));
    }

    @Test
    void markUserOffline_ShouldReturnFalse() {
        userPresenceService.markUserOnline(userId);
        userPresenceService.markUserOffline(userId);
        assertFalse(userPresenceService.isUserOnline(userId));
    }

    @Test
    void isUserOnline_ForNewUser_ShouldReturnFalse() {
        assertFalse(userPresenceService.isUserOnline(UUID.randomUUID()));
    }
}
