package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.models.ERole;
import iuh.fit.se.minizalobackend.models.Role;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.payload.request.SignupRequest;
import iuh.fit.se.minizalobackend.repository.RoleRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

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
    private MinioService minioService; // Assuming MinioService is a dependency

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(userRepository, roleRepository, passwordEncoder, minioService);
    }

    @Test
    void registerNewUser_Success_UserRole() {
        SignupRequest signupRequest = new SignupRequest("testuser", "test@example.com", "password123", null); // Default role

        when(userRepository.existsByUsername(signupRequest.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(signupRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(signupRequest.getPassword())).thenReturn("encodedPassword");
        Role userRole = new Role();
        userRole.setName(ERole.ROLE_USER);
        when(roleRepository.findByName(ERole.ROLE_USER)).thenReturn(Optional.of(userRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.registerNewUser(signupRequest);

        verify(userRepository, times(1)).existsByUsername("testuser");
        verify(userRepository, times(1)).existsByEmail("test@example.com");
        verify(passwordEncoder, times(1)).encode("password123");
        verify(roleRepository, times(1)).findByName(ERole.ROLE_USER);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void registerNewUser_Failure_UsernameAlreadyExists() {
        SignupRequest signupRequest = new SignupRequest("existinguser", "test@example.com", "password123", null);

        when(userRepository.existsByUsername(signupRequest.getUsername())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                userService.registerNewUser(signupRequest));

        assertEquals("Error: Username is already taken!", exception.getMessage());
        verify(userRepository, times(1)).existsByUsername("existinguser");
        verify(userRepository, never()).existsByEmail(anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verify(roleRepository, never()).findByName(any(ERole.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerNewUser_Failure_EmailAlreadyExists() {
        SignupRequest signupRequest = new SignupRequest("testuser", "existing@example.com", "password123", null);

        when(userRepository.existsByUsername(signupRequest.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(signupRequest.getEmail())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                userService.registerNewUser(signupRequest));

        assertEquals("Error: Email is already in use!", exception.getMessage());
        verify(userRepository, times(1)).existsByUsername("testuser");
        verify(userRepository, times(1)).existsByEmail("existing@example.com");
        verify(passwordEncoder, never()).encode(anyString());
        verify(roleRepository, never()).findByName(any(ERole.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerNewUser_Success_AdminRole() {
        SignupRequest signupRequest = new SignupRequest("adminuser", "admin@example.com", "adminpass", Set.of("admin"));

        when(userRepository.existsByUsername(signupRequest.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(signupRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(signupRequest.getPassword())).thenReturn("encodedAdminPassword");
        Role adminRole = new Role();
        adminRole.setName(ERole.ROLE_ADMIN);
        when(roleRepository.findByName(ERole.ROLE_ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.registerNewUser(signupRequest);

        verify(userRepository, times(1)).existsByUsername("adminuser");
        verify(userRepository, times(1)).existsByEmail("admin@example.com");
        verify(passwordEncoder, times(1)).encode("adminpass");
        verify(roleRepository, times(1)).findByName(ERole.ROLE_ADMIN);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void registerNewUser_Failure_RoleNotFound() {
        SignupRequest signupRequest = new SignupRequest("testuser", "test@example.com", "password123", Set.of("nonexistent"));

        when(userRepository.existsByUsername(signupRequest.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(signupRequest.getEmail())).thenReturn(false);
        when(roleRepository.findByName(any(ERole.class))).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                userService.registerNewUser(signupRequest));

        assertEquals("Error: User role is not found.", exception.getMessage()); // Default case if no specific role is matched or found
        verify(userRepository, times(1)).existsByUsername("testuser");
        verify(userRepository, times(1)).existsByEmail("test@example.com");
        verify(passwordEncoder, times(1)).encode(anyString());
        verify(roleRepository, times(1)).findByName(any(ERole.class));
        verify(userRepository, never()).save(any(User.class));
    }
}
