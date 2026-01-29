package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.models.ERole;
import iuh.fit.se.minizalobackend.models.Role;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.payload.request.SignupRequest;
import iuh.fit.se.minizalobackend.payload.request.UserProfileUpdateRequest;
import iuh.fit.se.minizalobackend.payload.response.UserProfileResponse;
import iuh.fit.se.minizalobackend.repository.RoleRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.MinioService;
import iuh.fit.se.minizalobackend.services.UserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final MinioService minioService;
    private final PasswordEncoder encoder;
    private final RoleRepository roleRepository;

    public UserServiceImpl(UserRepository userRepository, MinioService minioService, PasswordEncoder encoder,
            RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.minioService = minioService;
        this.encoder = encoder;
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public void registerNewUser(SignupRequest signupRequest) {
        long startTime = System.nanoTime();
        logger.debug("Starting registration for user: {}", signupRequest.getPhone());

        // Use phone as the unique username
        if (userRepository.existsByUsername(signupRequest.getPhone())) {
            throw new IllegalArgumentException("Error: Phone number is already registered!");
        }

        if (userRepository.existsByEmail(signupRequest.getEmail())) {
            throw new IllegalArgumentException("Error: Email is already in use!");
        }

        // Create new user's account
        User user = new User(
                signupRequest.getPhone(),
                signupRequest.getEmail(),
                encoder.encode(signupRequest.getPassword()));

        user.setDisplayName(signupRequest.getName());

        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                .orElseThrow(() -> new IllegalArgumentException("Error: User role is not found."));
        roles.add(userRole);

        user.setRoles(roles);
        userRepository.save(user);
        userRepository.flush();

        long endTime = System.nanoTime();
        long durationMillis = (endTime - startTime) / 1_000_000;
        logger.info("User registration for {} completed in {} ms", signupRequest.getPhone(), durationMillis);
    }

    @Override
    public UserProfileResponse getCurrentUserProfile(UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return mapUserToUserProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UserDetails userDetails, UserProfileUpdateRequest request) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (request.getDisplayName() != null && !request.getDisplayName().isEmpty()) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getStatusMessage() != null) {
            user.setStatusMessage(request.getStatusMessage());
        }

        return mapUserToUserProfileResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserProfileResponse uploadAvatar(UserDetails userDetails, MultipartFile avatarFile) throws IOException {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!avatarFile.isEmpty()) {
            String avatarUrl = minioService.uploadFile(
                    avatarFile,
                    "avatars/" + user.getId() + "/",
                    avatarFile.getOriginalFilename());
            user.setAvatarUrl(avatarUrl);
        }
        return mapUserToUserProfileResponse(userRepository.save(user));
    }

    @Override
    public List<UserProfileResponse> searchUsers(String query) {
        return userRepository.findByUsernameContainingIgnoreCase(query).stream()
                .map(this::mapUserToUserProfileResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<User> getUserById(UUID id) {
        return userRepository.findById(id);
    }

    @Override
    public UserProfileResponse mapUserToUserProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getStatusMessage(),
                user.getLastSeen(),
                user.getIsOnline());
    }

    @Override
    @Transactional
    public void updateFcmToken(UUID userId, String token) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        user.setFcmToken(token);
        userRepository.save(user);
    }
}
