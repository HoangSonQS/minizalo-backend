package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.payload.request.UserProfileUpdateRequest;
import iuh.fit.se.minizalobackend.payload.response.UserResponse;
import iuh.fit.se.minizalobackend.security.services.UserDetailsImpl;
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

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    // Constants for file validation
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList("image/jpeg", "image/png", "image/gif");

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getCurrentUserProfile(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        UserResponse userProfile = userService.getCurrentUserProfile(userDetails);
        return ResponseEntity.ok(userProfile);
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody UserProfileUpdateRequest request) {
        UserResponse updatedProfile = userService.updateProfile(userDetails, request);
        return ResponseEntity.ok(updatedProfile);
    }

    @PutMapping("/avatar")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<?> uploadAvatar(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam("file") MultipartFile file) {

        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity
                    .badRequest()
                    .body("Error: File size must not exceed " + (MAX_FILE_SIZE / (1024 * 1024)) + "MB!");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            return ResponseEntity
                    .badRequest()
                    .body("Error: Only JPEG, PNG, and GIF image formats are allowed!");
        }

        try {
            UserResponse updatedProfile = userService.uploadAvatar(userDetails, file);
            return ResponseEntity.ok(updatedProfile);
        } catch (IOException e) {
            return ResponseEntity
                    .internalServerError()
                    .body("Error: Could not upload the avatar due to an internal server error: " + e.getMessage());
        } catch (Exception e) {
            // Catch any other unexpected exceptions
            return ResponseEntity
                    .internalServerError()
                    .body("Error: An unexpected error occurred during avatar upload: " + e.getMessage());
        }
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> searchUsers(@RequestParam String q) {
        List<UserResponse> users = userService.searchUsers(q);
        return ResponseEntity.ok(users);
    }
}
