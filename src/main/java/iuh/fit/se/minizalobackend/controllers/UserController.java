package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.payload.request.UserProfileUpdateRequest;
import iuh.fit.se.minizalobackend.payload.response.UserProfileResponse;
import iuh.fit.se.minizalobackend.security.services.UserDetailsImpl;
import iuh.fit.se.minizalobackend.services.UserPresenceService;
import iuh.fit.se.minizalobackend.services.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserPresenceService userPresenceService;

    // Constants for file validation
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList("image/jpeg", "image/png", "image/gif");

    public UserController(UserService userService, UserPresenceService userPresenceService) {
        this.userService = userService;
        this.userPresenceService = userPresenceService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> getCurrentUserProfile(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        UserProfileResponse userProfile = userService.getCurrentUserProfile(userDetails);
        return ResponseEntity.ok(userProfile);
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody UserProfileUpdateRequest request) {
        UserProfileResponse updatedProfile = userService.updateProfile(userDetails, request);
        return ResponseEntity.ok(updatedProfile);
    }

    @PutMapping("/avatar")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<?> uploadAvatar(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam("file") MultipartFile file) {

        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity
                    .badRequest()
                    .body("Error: File size must not exceed " + (MAX_FILE_SIZE / (1024 * 1024)) + "MB!");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            return ResponseEntity
                    .badRequest()
                    .body("Error: Only JPEG, PNG, and GIF image formats are allowed!");
        }

        try {
            UserProfileResponse updatedProfile = userService.uploadAvatar(userDetails, file);
            return ResponseEntity.ok(updatedProfile);
        } catch (IOException e) {
            return ResponseEntity
                    .internalServerError()
                    .body("Error: Could not upload the avatar due to an internal server error: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .body("Error: An unexpected error occurred during avatar upload: " + e.getMessage());
        }
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<List<UserProfileResponse>> searchUsers(@RequestParam String q) {
        List<UserProfileResponse> users = userService.searchUsers(q);
        return ResponseEntity.ok(users);
    }

    @PutMapping("/fcm-token")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Void> updateFcmToken(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestBody String token) {
        userService.updateFcmToken(userDetails.getId(), token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/status")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Map<UUID, Boolean>> getUsersStatus(@RequestBody List<UUID> userIds) {
        Map<UUID, Boolean> statusMap = userIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        userPresenceService::isUserOnline));
        return ResponseEntity.ok(statusMap);
    }

    @PostMapping("/mute")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Void> muteConversation(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody iuh.fit.se.minizalobackend.dtos.request.MuteConversationRequest request) {
        userService.muteConversation(userDetails.getId(), request);
        return ResponseEntity.ok().build();
    }
}
