package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.dtos.request.ChangePasswordRequest;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.payload.request.SignupRequest;
import iuh.fit.se.minizalobackend.payload.request.UserProfileUpdateRequest;
import iuh.fit.se.minizalobackend.payload.response.UserProfileResponse;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserService {
    void registerNewUser(SignupRequest signupRequest);

    UserProfileResponse getCurrentUserProfile(UserDetails userDetails);

    UserProfileResponse updateProfile(UserDetails userDetails, UserProfileUpdateRequest request);

    UserProfileResponse uploadAvatar(UserDetails userDetails, MultipartFile avatarFile) throws IOException;

    List<UserProfileResponse> searchUsers(String query);

    Optional<User> getUserById(UUID id);

    UserProfileResponse mapUserToUserProfileResponse(User user);

    void updateFcmToken(UUID userId, String token);

    void changePassword(UUID userId, ChangePasswordRequest request);

    void muteConversation(UUID userId, iuh.fit.se.minizalobackend.dtos.request.MuteConversationRequest request);

    void updateOnlineStatus(UUID userId, boolean isOnline);
}
