package iuh.fit.se.minizalobackend.services;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private User testUser;
    private final UUID userId = UUID.randomUUID();
    private final String fcmToken = "valid-token";

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(userId);
        testUser.setFcmToken(fcmToken);
    }

    @Test
    void sendNotification_Success() throws FirebaseMessagingException {
        try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any())).thenReturn("message-id");

            notificationService.sendNotification(userId, fcmToken, "Title", "Body");

            verify(firebaseMessaging, times(1)).send(any());
            verify(userRepository, never()).save(any());
        }
    }

    @Test
    void sendNotification_DeadToken_ShouldCleanup() throws FirebaseMessagingException {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);

        try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any())).thenThrow(exception);

            notificationService.sendNotification(userId, fcmToken, "Title", "Body");

            verify(userRepository, times(1)).save(testUser);
            assert testUser.getFcmToken() == null;
        }
    }
}
