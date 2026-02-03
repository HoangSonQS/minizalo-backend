package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.impl.UserPresenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserPresenceServiceTest {

    private UserPresenceService userPresenceService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userPresenceService = new UserPresenceServiceImpl(userRepository, messagingTemplate);
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

    @Test
    void heartbeat_ShouldMarkUserOnline() {
        // Mock findById to avoid NPE in ifPresent
        iuh.fit.se.minizalobackend.models.User user = new iuh.fit.se.minizalobackend.models.User();
        user.setId(userId);
        org.mockito.Mockito.when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));

        userPresenceService.heartbeat(userId);
        assertTrue(userPresenceService.isUserOnline(userId));
        // Verify save is called (it might be called inside ifPresent)
        org.mockito.Mockito.verify(userRepository).save(user);
    }

    @Test
    void init_ShouldResetStatus() {
        // userPresenceService is verified as implementation
        if (userPresenceService instanceof UserPresenceServiceImpl) {
            ((UserPresenceServiceImpl) userPresenceService).init();
            org.mockito.Mockito.verify(userRepository).updateAllUsersOffline();
        }
    }
}
