package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.models.ERole;
import iuh.fit.se.minizalobackend.models.Role;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.payload.request.SignupRequest;
import iuh.fit.se.minizalobackend.payload.request.UserProfileUpdateRequest;
import iuh.fit.se.minizalobackend.payload.response.UserResponse;
import iuh.fit.se.minizalobackend.repository.RoleRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final MinioService minioService;
    private final PasswordEncoder encoder;
    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository, MinioService minioService, PasswordEncoder encoder,
            RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.minioService = minioService;
        this.encoder = encoder;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public void registerNewUser(SignupRequest signupRequest) {
        long startTime = System.nanoTime();
        logger.debug("Starting registration for user: {}", signupRequest.getUsername());

        // Ensure indexes are created on 'username' and 'email' columns for performance
        if (userRepository.existsByUsername(signupRequest.getUsername())) {
            throw new IllegalArgumentException("Error: Username is already taken!");
        }

        if (userRepository.existsByEmail(signupRequest.getEmail())) {
            throw new IllegalArgumentException("Error: Email is already in use!");
        }

        // Create new user's account
        User user = new User(
                signupRequest.getUsername(),
                signupRequest.getEmail(),
                encoder.encode(signupRequest.getPassword()));

        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                .orElseThrow(() -> new IllegalArgumentException("Error: User role is not found."));
        roles.add(userRole);

        user.setRoles(roles);
        userRepository.save(user);
        userRepository.flush();

        long endTime = System.nanoTime();
        long durationMillis = (endTime - startTime) / 1_000_000;
        logger.info("User registration for {} completed in {} ms", signupRequest.getUsername(), durationMillis);
    }

    public UserResponse getCurrentUserProfile(UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return mapUserToUserResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(UserDetails userDetails, UserProfileUpdateRequest request) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (request.getDisplayName() != null && !request.getDisplayName().isEmpty()) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getStatusMessage() != null) {
            user.setStatusMessage(request.getStatusMessage());
        }

        return mapUserToUserResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse uploadAvatar(UserDetails userDetails, MultipartFile avatarFile) throws IOException {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!avatarFile.isEmpty()) {
            String avatarUrl = minioService.uploadFile(
                    avatarFile,
                    "avatars/" + user.getId() + "/",
                    avatarFile.getOriginalFilename());
            user.setAvatarUrl(avatarUrl);
        }
        return mapUserToUserResponse(userRepository.save(user));
    }

    public List<UserResponse> searchUsers(String query) {
        return userRepository.findByUsernameContainingIgnoreCase(query).stream()
                .map(this::mapUserToUserResponse)
                .collect(Collectors.toList());
    }

    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
    }

    public UserResponse mapUserToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getStatusMessage(),
                user.getLastSeen(),
                user.getIsOnline());
    }
}
