package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.payload.request.FriendRequest;
import iuh.fit.se.minizalobackend.payload.response.FriendResponse;
import iuh.fit.se.minizalobackend.payload.response.MessageResponse;
import iuh.fit.se.minizalobackend.security.services.UserDetailsImpl;
import iuh.fit.se.minizalobackend.services.FriendService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @PostMapping("/request")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<?> sendFriendRequest(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody FriendRequest request) {
        try {
            FriendResponse response = friendService.sendFriendRequest(userDetails.getId(), request.getFriendId());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/accept/{requestId}")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<?> acceptFriendRequest(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable UUID requestId) {
        try {
            FriendResponse response = friendService.acceptFriendRequest(userDetails.getId(), requestId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @DeleteMapping("/reject/{requestId}")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<?> rejectFriendRequest(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable UUID requestId) {
        try {
            friendService.rejectFriendRequest(userDetails.getId(), requestId);
            return ResponseEntity.ok(new MessageResponse("Friend request rejected."));
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @DeleteMapping("/{friendId}")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<?> deleteFriend(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable UUID friendId) {
        try {
            friendService.deleteFriend(userDetails.getId(), friendId);
            return ResponseEntity.ok(new MessageResponse("Friend deleted."));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<List<FriendResponse>> getFriendsList(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        List<FriendResponse> friends = friendService.getFriendsList(userDetails.getId());
        return ResponseEntity.ok(friends);
    }

    @GetMapping("/requests")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<List<FriendResponse>> getPendingFriendRequests(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        List<FriendResponse> requests = friendService.getPendingFriendRequests(userDetails.getId());
        return ResponseEntity.ok(requests);
    }

    @PostMapping("/block/{userId}")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<?> blockUser(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable UUID userId) {
        try {
            friendService.blockUser(userDetails.getId(), userId);
            return ResponseEntity.ok(new MessageResponse("User blocked successfully."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @DeleteMapping("/block/{userId}")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<?> unblockUser(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable UUID userId) {
        try {
            friendService.unblockUser(userDetails.getId(), userId);
            return ResponseEntity.ok(new MessageResponse("User unblocked successfully."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }
}
