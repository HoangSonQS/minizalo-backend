package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.dtos.request.ChangePasswordRequest;
import iuh.fit.se.minizalobackend.dtos.request.MuteConversationRequest;
import iuh.fit.se.minizalobackend.models.ChatRoom;
import iuh.fit.se.minizalobackend.models.EPrivacyAudience;
import iuh.fit.se.minizalobackend.models.ERole;
import iuh.fit.se.minizalobackend.models.Role;
import iuh.fit.se.minizalobackend.models.RoomMember;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.payload.request.SignupRequest;
import iuh.fit.se.minizalobackend.payload.request.UserProfileUpdateRequest;
import iuh.fit.se.minizalobackend.payload.response.UserProfileResponse;
import iuh.fit.se.minizalobackend.repository.FriendRepository;
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
    private final FriendRepository friendRepository;
    private final iuh.fit.se.minizalobackend.security.JwtTokenProvider jwtTokenProvider;
    private final iuh.fit.se.minizalobackend.services.ChatRoomService chatRoomService;

    @Override
    @Transactional(readOnly = true)
    public void assertContactAvailableForSignup(String phone, String email) {
        String p = phone != null ? phone.trim() : "";
        if (p.isEmpty()) {
            throw new IllegalArgumentException("Số điện thoại không được để trống");
        }
        String e = email != null ? email.trim() : "";
        if (e.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập email");
        }
        if (Boolean.TRUE.equals(userRepository.existsByUsername(p))
                || Boolean.TRUE.equals(userRepository.existsByPhone(p))) {
            throw new IllegalArgumentException("Số điện thoại đã được đăng ký");
        }
        if (Boolean.TRUE.equals(userRepository.existsByEmailIgnoreCase(e))) {
            throw new IllegalArgumentException("Email đã được sử dụng");
        }
    }

    @Override
    @Transactional
    public void registerNewUser(SignupRequest signupRequest) {
        long startTime = System.nanoTime();
        log.debug("Starting registration for user: {}", signupRequest.getPhone());

        // Verify the verification token
        if (signupRequest.getVerificationToken() == null || signupRequest.getVerificationToken().isBlank()) {
            throw new IllegalArgumentException("Cần xác thực số điện thoại trước khi đăng ký");
        }
        try {
            String verifiedPhone = jwtTokenProvider.getPhoneFromVerificationToken(signupRequest.getVerificationToken());
            if (!verifiedPhone.equals(signupRequest.getPhone())) {
                throw new IllegalArgumentException("Mã xác thực không khớp với số điện thoại");
            }
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) throw e;
            throw new IllegalArgumentException("Mã xác thực không hợp lệ hoặc đã hết hạn");
        }

        assertContactAvailableForSignup(signupRequest.getPhone(), signupRequest.getEmail());

        // Create new user's account
        User user = new User(
                signupRequest.getPhone(),
                signupRequest.getEmail(),
                encoder.encode(signupRequest.getPassword()));

        user.setDisplayName(signupRequest.getName());
        user.setPhone(signupRequest.getPhone());

        if (signupRequest.getGender() != null) {
            user.setGender(signupRequest.getGender());
        }
        if (signupRequest.getDateOfBirth() != null) {
            user.setDateOfBirth(signupRequest.getDateOfBirth());
        }

        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                .orElseThrow(() -> new IllegalArgumentException("Error: User role is not found."));
        roles.add(userRole);

        user.setRoles(roles);
        userRepository.save(user);
        userRepository.flush();

        // Tạo phòng "Cloud của tôi" cho user mới đăng ký
        try {
            chatRoomService.initCloudRoom(user);
        } catch (Exception e) {
            log.warn("Failed to init cloud room for {}: {}", signupRequest.getPhone(), e.getMessage());
        }

        long endTime = System.nanoTime();
        long durationMillis = (endTime - startTime) / 1_000_000;
        log.info("User registration for {} completed in {} ms", signupRequest.getPhone(), durationMillis);
    }

    @Override
    @Transactional(readOnly = true)
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
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isEmpty()) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getCoverPhotoUrl() != null && !request.getCoverPhotoUrl().isEmpty()) {
            user.setCoverPhotoUrl(request.getCoverPhotoUrl());
        }
        if (request.getStatusMessage() != null) {
            user.setStatusMessage(request.getStatusMessage());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getDateOfBirth() != null) {
            user.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getBusinessDescription() != null) {
            user.setBusinessDescription(request.getBusinessDescription());
        }
        if (request.getAllowPhoneSearch() != null) {
            user.setAllowPhoneSearch(request.getAllowPhoneSearch());
        }
        if (request.getAllowMessagesFrom() != null) {
            user.setAllowMessagesFrom(request.getAllowMessagesFrom());
        }
        if (request.getAllowCallsFrom() != null) {
            user.setAllowCallsFrom(request.getAllowCallsFrom());
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
    @Transactional
    public UserProfileResponse uploadCoverPhoto(UserDetails userDetails, MultipartFile coverFile) throws IOException {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!coverFile.isEmpty()) {
            String coverUrl = minioService.uploadFile(
                    coverFile,
                    "covers/" + user.getId() + "/",
                    coverFile.getOriginalFilename());
            user.setCoverPhotoUrl(coverUrl);
        }
        return mapUserToUserProfileResponse(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserProfileResponse> searchUsers(String query, UUID currentUserId) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }

        // Nếu query là chuỗi toàn số: coi là số điện thoại, yêu cầu khớp chính xác
        if (q.matches("\\d+")) {
            Optional<User> targetUserOpt = userRepository.findByPhone(q);
            if (targetUserOpt.isEmpty()) {
                return List.of();
            }

            User targetUser = targetUserOpt.get();
            // Nếu là chính mình thì cho thấy
            if (targetUser.getId().equals(currentUserId)) {
                return List.of(mapUserToUserProfileResponse(targetUser));
            }

            // Nếu cho phép tìm qua SĐT thì cho thấy
            if (Boolean.TRUE.equals(targetUser.getAllowPhoneSearch())) {
                return List.of(mapUserToUserProfileResponse(targetUser));
            }

            // Nếu không cho phép, nhưng ĐÃ LÀ BẠN BÈ thì vẫn cho thấy
            User requester = userRepository.findById(currentUserId).orElse(null);
            if (requester != null) {
                boolean isFriend = friendRepository.findByUserAndFriend(requester, targetUser).isPresent()
                        || friendRepository.findByUserAndFriend(targetUser, requester).isPresent();
                if (isFriend) {
                    return List.of(mapUserToUserProfileResponse(targetUser));
                }
            }

            return List.of();
        }

        // Ngược lại (chứa chữ cái): tìm theo displayName hoặc username và lọc ra chính mình
        return userRepository.searchByDisplayNameOrUsername(q).stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .map(this::mapUserToUserProfileResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<User> getUserById(UUID id) {
        return userRepository.findById(id);
    }

    @Override
    public UserProfileResponse mapUserToUserProfileResponse(User user) {
        List<String> roleNames = user.getRoles() != null
                ? user.getRoles().stream()
                        .map(r -> r.getName().name())
                        .collect(Collectors.toList())
                : List.of();
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                minioService.ensurePublicUrl(user.getAvatarUrl()),
                minioService.ensurePublicUrl(user.getCoverPhotoUrl()),
                user.getStatusMessage(),
                user.getPhone(),
                user.getGender(),
                user.getDateOfBirth(),
                user.getBusinessDescription(),
                user.getLastSeen(),
                user.getIsOnline(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                roleNames,
                user.getAllowPhoneSearch(),
                user.getAllowMessagesFrom() != null
                        ? user.getAllowMessagesFrom()
                        : EPrivacyAudience.EVERYONE,
                user.getAllowCallsFrom() != null
                        ? user.getAllowCallsFrom()
                        : EPrivacyAudience.EVERYONE);
    }

    @Override
    @Transactional
    public void updateFcmToken(UUID userId, String token) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        String normalizedToken = token == null ? null : token.trim();
        if (normalizedToken != null && normalizedToken.length() >= 2
                && normalizedToken.startsWith("\"") && normalizedToken.endsWith("\"")) {
            normalizedToken = normalizedToken.substring(1, normalizedToken.length() - 1).trim();
        }
        user.setFcmToken(normalizedToken == null || normalizedToken.isBlank() ? null : normalizedToken);
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

    @Override
    @Transactional
    public void updateOnlineStatus(UUID userId, boolean isOnline) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        user.setIsOnline(isOnline);
        if (!isOnline) {
            user.setLastSeen(LocalDateTime.now());
        }

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void resetPassword(String phone, String newPassword) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with phone: " + phone));
                
        // Check new password is different from old password
        if (encoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from old password");
        }

        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password reset successfully for phone: {}", phone);
    }

    @Override
    @Transactional
    public void lockAccount(UUID userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!encoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu không chính xác");
        }

        user.setAccountLocked(true);
        user.setIsOnline(false);
        user.setLastSeen(LocalDateTime.now());
        userRepository.save(user);
    }
  
    @Override
    public List<UserProfileResponse> findUsersByPhoneNumbers(List<String> phoneNumbers, UUID currentUserId) {
        // Normalize phone numbers: strip spaces, dashes, and handle Vietnamese format (0xxx -> +84xxx)
        List<String> normalized = phoneNumbers.stream()
                .map(p -> p.replaceAll("[\\s\\-()]", ""))
                .filter(p -> !p.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        // Also generate alternative formats for matching
        // e.g., "0912345678" → "0912345678", "+84912345678", "84912345678"
        List<String> allVariants = normalized.stream()
                .flatMap(p -> {
                    java.util.stream.Stream.Builder<String> variants = java.util.stream.Stream.builder();
                    variants.add(p);
                    if (p.startsWith("0")) {
                        variants.add("+84" + p.substring(1));
                        variants.add("84" + p.substring(1));
                    } else if (p.startsWith("+84")) {
                        variants.add("0" + p.substring(3));
                        variants.add("84" + p.substring(3));
                    } else if (p.startsWith("84") && p.length() >= 11) {
                        variants.add("0" + p.substring(2));
                        variants.add("+" + p);
                    }
                    return variants.build();
                })
                .distinct()
                .collect(Collectors.toList());

        List<User> matchedUsers = userRepository.findByPhoneIn(allVariants);

        return matchedUsers.stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .filter(u -> Boolean.TRUE.equals(u.getAllowPhoneSearch())) // Dòng này thêm vào: Lọc ra ai cho phép
                .map(this::mapUserToUserProfileResponse)
                .collect(Collectors.toList());
    }
}
