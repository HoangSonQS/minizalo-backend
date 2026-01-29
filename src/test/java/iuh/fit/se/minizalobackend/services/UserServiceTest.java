package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.models.ERole;
import iuh.fit.se.minizalobackend.models.Role;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.payload.request.SignupRequest;
import iuh.fit.se.minizalobackend.repository.RoleRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MinioService minioService;

    @InjectMocks
    private UserServiceImpl userServiceImpl;

    @BeforeEach
    void setUp() {
        reset(userRepository, roleRepository, passwordEncoder, minioService);
    }

    @Test
    void registerNewUser_Success_UserRole() {
        SignupRequest signupRequest = new SignupRequest("Test User", "0987654321", "test@example.com", "password123");

        when(userRepository.existsByUsername(signupRequest.getPhone())).thenReturn(false);
        when(userRepository.existsByEmail(signupRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(signupRequest.getPassword())).thenReturn("encodedPassword");

        Role userRole = new Role();
        userRole.setName(ERole.ROLE_USER);
        when(roleRepository.findByName(ERole.ROLE_USER)).thenReturn(Optional.of(userRole));

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userServiceImpl.registerNewUser(signupRequest);

        verify(userRepository, times(1)).existsByUsername("0987654321");
        verify(userRepository, times(1)).existsByEmail("test@example.com");
        verify(passwordEncoder, times(1)).encode("password123");
        verify(roleRepository, times(1)).findByName(ERole.ROLE_USER);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void registerNewUser_Failure_UsernameAlreadyExists() {
        SignupRequest signupRequest = new SignupRequest("Existing User", "0987654321", "test@example.com",
                "password123");

        when(userRepository.existsByUsername(signupRequest.getPhone())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userServiceImpl.registerNewUser(signupRequest));

        assertEquals("Error: Phone number is already registered!", exception.getMessage());
        verify(userRepository, times(1)).existsByUsername("0987654321");
        verify(userRepository, never()).existsByEmail(anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verify(roleRepository, never()).findByName(any(ERole.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerNewUser_Failure_EmailAlreadyExists() {
        SignupRequest signupRequest = new SignupRequest("Test User", "0987654321", "existing@example.com",
                "password123");

        when(userRepository.existsByUsername(signupRequest.getPhone())).thenReturn(false);
        when(userRepository.existsByEmail(signupRequest.getEmail())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userServiceImpl.registerNewUser(signupRequest));

        assertEquals("Error: Email is already in use!", exception.getMessage());
        verify(userRepository, times(1)).existsByUsername("0987654321");
        verify(userRepository, times(1)).existsByEmail("existing@example.com");
        verify(passwordEncoder, never()).encode(anyString());
        verify(roleRepository, never()).findByName(any(ERole.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerNewUser_Failure_RoleNotFound() {
        SignupRequest signupRequest = new SignupRequest("Test User", "0987654321", "test@example.com", "password123");

        when(userRepository.existsByUsername(signupRequest.getPhone())).thenReturn(false);
        when(userRepository.existsByEmail(signupRequest.getEmail())).thenReturn(false);
        when(roleRepository.findByName(ERole.ROLE_USER)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userServiceImpl.registerNewUser(signupRequest));

        assertEquals("Error: User role is not found.", exception.getMessage());
        verify(userRepository, times(1)).existsByUsername("0987654321");
        verify(userRepository, times(1)).existsByEmail("test@example.com");
        verify(passwordEncoder, times(1)).encode(anyString());
        verify(roleRepository, times(1)).findByName(ERole.ROLE_USER);
        verify(userRepository, never()).save(any(User.class));
    }
}
