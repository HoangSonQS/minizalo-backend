package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.exception.TokenRefreshException;
import iuh.fit.se.minizalobackend.models.RefreshToken;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.repository.RefreshTokenRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.security.services.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private final long refreshTokenDurationDays = 1L; // 1 day for testing

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenDurationDays", refreshTokenDurationDays);
        reset(refreshTokenRepository, userRepository);
    }

    @Test
    void createRefreshToken_Success() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.empty());
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken createdToken = refreshTokenService.createRefreshToken(userId);

        assertNotNull(createdToken);
        assertNotNull(createdToken.getToken());
        assertEquals(user, createdToken.getUser());
        assertTrue(createdToken.getExpiryDate().isAfter(Instant.now()));

        verify(userRepository, times(1)).findById(userId);
        verify(refreshTokenRepository, times(1)).findByUser(user);
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    void createRefreshToken_UserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                refreshTokenService.createRefreshToken(userId));

        assertEquals("User not found with id: " + userId, exception.getMessage());
        verify(userRepository, times(1)).findById(userId);
        verify(refreshTokenRepository, never()).findByUser(any(User.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void createRefreshToken_ExistingTokenFoundAndUpdated() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        RefreshToken existingToken = new RefreshToken();
        existingToken.setUser(user);
        existingToken.setExpiryDate(Instant.now().plusMillis(10000)); // Still valid
        existingToken.setToken(UUID.randomUUID().toString());

        String initialExistingToken = existingToken.getToken();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.of(existingToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken createdToken = refreshTokenService.createRefreshToken(userId);

        assertNotNull(createdToken);
        assertNotEquals(initialExistingToken, createdToken.getToken()); // Token should be rotated
        assertEquals(user, createdToken.getUser());
        assertTrue(createdToken.getExpiryDate().isAfter(Instant.now()));

        verify(userRepository, times(1)).findById(userId);
        verify(refreshTokenRepository, times(1)).findByUser(user);
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }


    @Test
    void verifyExpiration_TokenNotExpired() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setExpiryDate(Instant.now().plusSeconds(refreshTokenDurationDays * 24 * 60 * 60));

        RefreshToken verifiedToken = refreshTokenService.verifyExpiration(refreshToken);

        assertNotNull(verifiedToken);
        assertEquals(refreshToken, verifiedToken);
    }

    @Test
    void verifyExpiration_TokenExpired() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setExpiryDate(Instant.now().minusMillis(1000)); // Expired

        TokenRefreshException exception = assertThrows(TokenRefreshException.class, () ->
                refreshTokenService.verifyExpiration(refreshToken));

        String expectedMessage = String.format("Failed for [%s]: %s", refreshToken.getToken(), "Refresh token was expired. Please make a new signin request");
        assertEquals(expectedMessage, exception.getMessage());

        verify(refreshTokenRepository, times(1)).delete(refreshToken);
    }

    @Test
    void findByToken_Success() {
        String token = "someToken";
        RefreshToken refreshToken = new RefreshToken();
        when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.of(refreshToken));

        Optional<RefreshToken> foundToken = refreshTokenService.findByToken(token);

        assertTrue(foundToken.isPresent());
        assertEquals(refreshToken, foundToken.get());
        verify(refreshTokenRepository, times(1)).findByToken(token);
    }

    @Test
    void findByToken_NotFound() {
        String token = "nonExistentToken";
        when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        Optional<RefreshToken> foundToken = refreshTokenService.findByToken(token);

        assertFalse(foundToken.isPresent());
        verify(refreshTokenRepository, times(1)).findByToken(token);
    }

    @Test
    void deleteByUserId_Success() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.deleteByUser(user)).thenReturn(1);

        refreshTokenService.deleteByUserId(userId);

        verify(userRepository, times(1)).findById(userId);
        verify(refreshTokenRepository, times(1)).deleteByUser(user);
    }

    @Test
    void deleteByUserId_UserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                refreshTokenService.deleteByUserId(userId));

        assertEquals("User not found with id: " + userId, exception.getMessage());
        verify(userRepository, times(1)).findById(userId);
        verify(refreshTokenRepository, never()).deleteByUser(any(User.class));
    }
}
