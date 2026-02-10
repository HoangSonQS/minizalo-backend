package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.payload.request.FriendCategoryAssignRequest;
import iuh.fit.se.minizalobackend.payload.request.FriendCategoryUpsertRequest;
import iuh.fit.se.minizalobackend.payload.response.FriendCategoryAssignmentResponse;
import iuh.fit.se.minizalobackend.payload.response.FriendCategoryResponse;
import iuh.fit.se.minizalobackend.payload.response.MessageResponse;
import iuh.fit.se.minizalobackend.security.services.UserDetailsImpl;
import iuh.fit.se.minizalobackend.services.FriendCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/friend-categories")
@RequiredArgsConstructor
public class FriendCategoryController {
    private final FriendCategoryService friendCategoryService;

    @GetMapping
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<List<FriendCategoryResponse>> listCategories(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(friendCategoryService.listCategories(userDetails.getId()));
    }

    @PostMapping
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<?> createCategory(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody FriendCategoryUpsertRequest request) {
        try {
            return ResponseEntity.ok(friendCategoryService.createCategory(userDetails.getId(), request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<?> updateCategory(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable UUID categoryId,
            @Valid @RequestBody FriendCategoryUpsertRequest request) {
        try {
            return ResponseEntity.ok(friendCategoryService.updateCategory(userDetails.getId(), categoryId, request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<?> deleteCategory(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable UUID categoryId) {
        try {
            friendCategoryService.deleteCategory(userDetails.getId(), categoryId);
            return ResponseEntity.ok(new MessageResponse("Category deleted."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @GetMapping("/assignments")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<List<FriendCategoryAssignmentResponse>> listAssignments(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(friendCategoryService.listAssignments(userDetails.getId()));
    }

    @PostMapping("/assignments")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<?> assignCategory(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody FriendCategoryAssignRequest request) {
        try {
            return ResponseEntity.ok(friendCategoryService.assignCategory(
                    userDetails.getId(),
                    request.getTargetUserId(),
                    request.getCategoryId()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }
}

