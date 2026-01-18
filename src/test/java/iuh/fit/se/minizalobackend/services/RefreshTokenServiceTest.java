package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.exception.TokenRefreshException;
import iuh.fit.se.minizalobackend.models.RefreshToken;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.repository.RefreshTokenRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.impl.RefreshTokenServiceImpl;

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

    private RefreshTokenServiceImpl refreshTokenService;

    private final long refreshTokenExpirationDays = 7L;

    @BeforeEach

    void setUp() {

        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpirationDays", refreshTokenExpirationDays);

    }

    @Test

    void createRefreshToken_Success() {

        String userId = UUID.randomUUID().toString();

        User user = new User();

        user.setId(UUID.fromString(userId));

        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));

        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken createdToken = refreshTokenService.createRefreshToken(userId);

        assertNotNull(createdToken);

        assertNotNull(createdToken.getToken());

        assertEquals(user, createdToken.getUser());

        assertTrue(createdToken.getExpiryDate().isAfter(Instant.now()));

        verify(userRepository, times(1)).findById(UUID.fromString(userId));

        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));

    }

    @Test

    void createRefreshToken_UserNotFound() {

        String userId = UUID.randomUUID().toString();

        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.empty());

        assertThrows(java.lang.IllegalArgumentException.class, () ->

        refreshTokenService.createRefreshToken(userId));

        verify(userRepository, times(1)).findById(UUID.fromString(userId));

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));

    }

    @Test

    void verifyExpiration_TokenNotExpired() {

        RefreshToken refreshToken = new RefreshToken();

        refreshToken.setExpiryDate(Instant.now().plusSeconds(refreshTokenExpirationDays * 24 * 60 * 60));

        RefreshToken verifiedToken = refreshTokenService.verifyExpiration(refreshToken);

        assertNotNull(verifiedToken);

        assertEquals(refreshToken, verifiedToken);

    }

    @Test

    void verifyExpiration_TokenExpired() {

        RefreshToken refreshToken = new RefreshToken();

        refreshToken.setToken("test-token");

        refreshToken.setExpiryDate(Instant.now().minusMillis(1000)); // Expired

        TokenRefreshException exception = assertThrows(TokenRefreshException.class, () ->

        refreshTokenService.verifyExpiration(refreshToken));

        String expectedMessage = "Refresh token was expired. Please make a new signin request";

        assertTrue(exception.getMessage().contains(expectedMessage));

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

        String userId = UUID.randomUUID().toString();

        User user = new User();

        user.setId(UUID.fromString(userId));

        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));

        refreshTokenService.deleteByUserId(userId);

        verify(userRepository, times(1)).findById(UUID.fromString(userId));

        verify(refreshTokenRepository, times(1)).deleteByUser(user);

        verify(refreshTokenRepository, times(1)).flush();

    }

    @Test

    void deleteByUserId_UserNotFound() {

        String userId = UUID.randomUUID().toString();

        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.empty());

        refreshTokenService.deleteByUserId(userId);

        verify(userRepository, times(1)).findById(UUID.fromString(userId));

        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));

    }

}
