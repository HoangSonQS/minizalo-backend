package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.dtos.request.AddPollOptionRequest;
import iuh.fit.se.minizalobackend.dtos.request.CreatePollRequest;
import iuh.fit.se.minizalobackend.dtos.request.PollVoteRequest;
import iuh.fit.se.minizalobackend.dtos.response.PollResponse;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.security.services.UserDetailsImpl;
import iuh.fit.se.minizalobackend.services.PollService;
import iuh.fit.se.minizalobackend.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/polls")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<PollResponse> createPoll(
            @Valid @RequestBody CreatePollRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User creator = userService.getUserById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(pollService.createPoll(request, creator));
    }

    @GetMapping("/room/{roomId}")
    public ResponseEntity<List<PollResponse>> getPollsInRoom(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User viewer = userService.getUserById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(pollService.getPollsInRoom(roomId, viewer));
    }

    @PostMapping("/options")
    public ResponseEntity<PollResponse> addOptionToPoll(
            @Valid @RequestBody AddPollOptionRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User initiator = userService.getUserById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(pollService.addOptionToPoll(request, initiator));
    }

    @PostMapping("/vote")
    public ResponseEntity<PollResponse> votePoll(
            @Valid @RequestBody PollVoteRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User voter = userService.getUserById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(pollService.votePoll(request, voter));
    }

    @PutMapping("/{pollId}/close")
    public ResponseEntity<PollResponse> closePoll(
            @PathVariable UUID pollId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User initiator = userService.getUserById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(pollService.closePoll(pollId, initiator));
    }

    @DeleteMapping("/{pollId}")
    public ResponseEntity<Void> deletePoll(
            @PathVariable UUID pollId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User initiator = userService.getUserById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        pollService.deletePoll(pollId, initiator);
        return ResponseEntity.ok().build();
    }
}
