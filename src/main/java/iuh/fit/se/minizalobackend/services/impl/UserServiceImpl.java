package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.dtos.request.ChangePasswordRequest;
import iuh.fit.se.minizalobackend.dtos.request.MuteConversationRequest;
import iuh.fit.se.minizalobackend.models.ChatRoom;
import iuh.fit.se.minizalobackend.models.ERole;
import iuh.fit.se.minizalobackend.models.Role;
import iuh.fit.se.minizalobackend.models.RoomMember;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.payload.request.SignupRequest;
import iuh.fit.se.minizalobackend.payload.request.UserProfileUpdateRequest;
import iuh.fit.se.minizalobackend.payload.response.UserProfileResponse;
import iuh.fit.se.minizalobackend.repository.GroupRepository;
import iuh.fit.se.minizalobackend.repository.RoleRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.MinioService;
import iuh.fit.se.minizalobackend.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final MinioService minioService;
    private final PasswordEncoder encoder;
    private final RoleRepository roleRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final GroupRepository groupRepository;

    @Override
    @Transactional
    public void registerNewUser(SignupRequest signupRequest) {
        long startTime = System.nanoTime();
        log.debug("Starting registration for user: {}", signupRequest.getPhone());

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
        log.info("User registration for {} completed in {} ms", signupRequest.getPhone(), durationMillis);
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

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        log.info("Changing password for user: {}", userId);

        // Validate confirm password matches
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }

        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Validate old password
        if (!encoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Old password is incorrect");
        }

        // Check new password is different from old password
        if (encoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from old password");
        }

        // Update password
        user.setPassword(encoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed successfully for user: {}", userId);
    }

    @Override
    @Transactional
    public void muteConversation(UUID userId, MuteConversationRequest request) {
        ChatRoom room = groupRepository.findById(request.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        RoomMember member = roomMemberRepository.findByRoomAndUser(room, currentUser)
                .orElseThrow(() -> new IllegalArgumentException("You are not a member of this conversation"));

        if (request.isMute()) {
            member.setMuted(true);
            if (request.getDurationMinutes() != null && request.getDurationMinutes() > 0) {
                member.setMuteUntil(LocalDateTime.now().plusMinutes(request.getDurationMinutes()));
            } else {
                member.setMuteUntil(null); // Forever
            }
        } else {
            member.setMuted(false);
            member.setMuteUntil(null);
        }

        roomMemberRepository.save(member);
    }
}
