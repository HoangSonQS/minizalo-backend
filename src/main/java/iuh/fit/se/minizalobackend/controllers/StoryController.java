package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.dtos.response.StoryResponse;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.models.ModerationFlag;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.repository.ModerationFlagRepository;
import iuh.fit.se.minizalobackend.services.StoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
@Tag(name = "Story Controller", description = "APIs for Story/Diary management")
@SecurityRequirement(name = "bearerAuth")
public class StoryController {

    private final StoryService storyService;
    private final UserRepository userRepository;
    private final ModerationFlagRepository moderationFlagRepository;

    @PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a new story")
    public ResponseEntity<StoryResponse> createStory(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "storyType", defaultValue = "PHOTO") String storyType,
            @RequestParam(value = "privacy", defaultValue = "ALL_FRIENDS") String privacy,
            @RequestParam(value = "permittedUserIds", required = false) List<String> permittedUserIds,
            @RequestParam(value = "backgroundConfig", required = false) String backgroundConfig) throws IOException {
        User user = getCurrentUser();
        return ResponseEntity.ok(storyService.createStory(user, file, caption, storyType, privacy, permittedUserIds, backgroundConfig));
    }

    @PostMapping("/reaction")
    @Operation(summary = "Add reaction to a story")
    public ResponseEntity<Void> addReaction(
            @RequestParam("userId") String userId,
            @RequestParam("createdAt") String createdAt,
            @RequestParam("type") String type) {
        User user = getCurrentUser();
        storyService.addReaction(user, userId, createdAt, type);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/feed")
    @Operation(summary = "Get story feed (friends + self)")
    public ResponseEntity<List<StoryResponse>> getFeed() {
        User user = getCurrentUser();
        return ResponseEntity.ok(storyService.getFeed(user));
    }

    @GetMapping("/me")
    @Operation(summary = "Get my active stories")
    public ResponseEntity<List<StoryResponse>> getMyStories() {
        User user = getCurrentUser();
        return ResponseEntity.ok(storyService.getMyStories(user));
    }

    @DeleteMapping("")
    @Operation(summary = "Delete a story")
    public ResponseEntity<Void> deleteStory(@RequestParam("createdAt") String createdAt) {
        User user = getCurrentUser();
        storyService.deleteStory(user, createdAt);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/privacy")
    @Operation(summary = "Update story privacy")
    public ResponseEntity<Void> updatePrivacy(
            @RequestParam("createdAt") String createdAt,
            @RequestParam("privacy") String privacy,
            @RequestParam(value = "permittedUserIds", required = false) List<String> permittedUserIds) {
        User user = getCurrentUser();
        storyService.updatePrivacy(user, createdAt, privacy, permittedUserIds);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/view")
    @Operation(summary = "Mark a story as viewed")
    public ResponseEntity<Void> viewStory(
            @RequestParam("userId") String userId,
            @RequestParam("createdAt") String createdAt) {
        User user = getCurrentUser();
        storyService.viewStory(user, userId, createdAt);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/report")
    @Operation(summary = "Report a story")
    public ResponseEntity<?> reportStory(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> body) {
        User reporter = getCurrentUser();
        String storyUserId = body.get("storyUserId");
        String storyId = body.get("storyId"); // createdAt value
        String reason = body.get("reason");
        String previewContent = body.get("content"); // optional caption

        if (storyUserId == null || storyId == null) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "Thiếu thông tin story"));
        }

        // Prevent duplicate reports
        boolean alreadyReported = moderationFlagRepository
            .existsByTargetIdAndReporterIdAndStatus(storyId, reporter.getId().toString(), "PENDING");
        if (alreadyReported) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "Bạn đã báo cáo story này rồi"));
        }

        ModerationFlag flag = new ModerationFlag();
        flag.setTargetType("STORY");
        flag.setTargetId(storyId);
        flag.setSenderId(storyUserId); // story owner
        flag.setReporterId(reporter.getId().toString());
        flag.setContent(previewContent != null ? previewContent : "Story đa phương tiện");
        flag.setReason(reason != null ? reason : "INAPPROPRIATE");
        flag.setStatus("PENDING");
        moderationFlagRepository.save(flag);

        return ResponseEntity.ok(java.util.Map.of("message", "Đã gửi báo cáo. Chúng tôi sẽ kiểm tra sớm nhất có thể."));
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
