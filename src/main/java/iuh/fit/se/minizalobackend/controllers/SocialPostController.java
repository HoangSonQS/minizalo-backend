package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.dtos.response.SocialPostResponse;
import iuh.fit.se.minizalobackend.models.EFriendStatus;
import iuh.fit.se.minizalobackend.models.Friend;
import iuh.fit.se.minizalobackend.models.SocialPost;
import iuh.fit.se.minizalobackend.models.SocialPostComment;
import iuh.fit.se.minizalobackend.models.SocialPostMedia;
import iuh.fit.se.minizalobackend.models.SocialPostReaction;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.repository.FriendRepository;
import iuh.fit.se.minizalobackend.repository.SocialPostCommentRepository;
import iuh.fit.se.minizalobackend.repository.SocialPostMediaRepository;
import iuh.fit.se.minizalobackend.repository.SocialPostReactionRepository;
import iuh.fit.se.minizalobackend.repository.SocialPostRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.MinioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Tag(name = "Social Post Controller", description = "APIs for timeline posts")
@SecurityRequirement(name = "bearerAuth")
public class SocialPostController {

    private final SocialPostRepository postRepository;
    private final SocialPostMediaRepository mediaRepository;
    private final SocialPostCommentRepository commentRepository;
    private final SocialPostReactionRepository reactionRepository;
    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final MinioService minioService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a timeline post")
    public ResponseEntity<SocialPostResponse> createPost(
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "privacy", defaultValue = "ALL_FRIENDS") String privacy,
            @RequestParam(value = "permittedUserIds", required = false) List<String> permittedUserIds) throws IOException {
        User user = getCurrentUser();
        List<MultipartFile> uploadFiles = new ArrayList<>();
        if (files != null) uploadFiles.addAll(files.stream().filter(f -> f != null && !f.isEmpty()).toList());
        if (file != null && !file.isEmpty()) uploadFiles.add(file);

        String mediaUrl = null;
        String mediaType = null;
        if (file != null && !file.isEmpty()) {
            String contentType = file.getContentType();
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            mediaUrl = minioService.uploadFile(file, "posts/", fileName);
            mediaType = isVideoFile(contentType, file.getOriginalFilename()) ? "VIDEO" : "IMAGE";
        } else if (!uploadFiles.isEmpty()) {
            MultipartFile first = uploadFiles.get(0);
            String contentType = first.getContentType();
            String fileName = UUID.randomUUID() + "_" + first.getOriginalFilename();
            mediaUrl = minioService.uploadFile(first, "posts/", fileName);
            mediaType = isVideoFile(contentType, first.getOriginalFilename()) ? "VIDEO" : "IMAGE";
        }
        SocialPost post = SocialPost.builder()
                .user(user)
                .content(content)
                .mediaUrl(mediaUrl)
                .mediaType(mediaType)
                .privacy(normalizePrivacy(privacy))
                .permittedUserIds(joinIds(permittedUserIds))
                .build();
        SocialPost saved = postRepository.save(post);
        int startIndex = mediaUrl != null ? 1 : 0;
        if (mediaUrl != null) {
            mediaRepository.save(SocialPostMedia.builder()
                    .post(saved)
                    .mediaUrl(mediaUrl)
                    .mediaType(mediaType)
                    .sortOrder(0)
                    .build());
        }
        for (int i = startIndex; i < uploadFiles.size(); i++) {
            MultipartFile current = uploadFiles.get(i);
            String currentFileName = UUID.randomUUID() + "_" + current.getOriginalFilename();
            String currentUrl = minioService.uploadFile(current, "posts/", currentFileName);
            String currentType = isVideoFile(current.getContentType(), current.getOriginalFilename()) ? "VIDEO" : "IMAGE";
            mediaRepository.save(SocialPostMedia.builder()
                    .post(saved)
                    .mediaUrl(currentUrl)
                    .mediaType(currentType)
                    .sortOrder(i)
                    .build());
        }

        SocialPostResponse response = map(saved);
        messagingTemplate.convertAndSend("/topic/social/posts", Map.of(
                "type", "POST_CREATED",
                "postId", response.getId(),
                "userId", response.getUserId()
        ));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{postId}/comments/{commentId}")
    @Operation(summary = "Delete my comment from a timeline post")
    public ResponseEntity<SocialPostResponse> deleteComment(
            @PathVariable UUID postId,
            @PathVariable UUID commentId) {
        User user = getCurrentUser();
        SocialPost post = getVisiblePostOrThrow(user, postId);
        SocialPostComment comment = commentRepository.findWithPostAndUserById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));
        if (!comment.getPost().getId().equals(postId)) throw new RuntimeException("Comment not found");
        if (!comment.getUser().getId().equals(user.getId())) throw new RuntimeException("Cannot delete this comment");
        commentRepository.delete(comment);
        SocialPostResponse response = map(post);
        broadcastPostChanged("POST_COMMENT_DELETED", postId, user.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{postId}/comments")
    @Operation(summary = "Comment on a timeline post")
    public ResponseEntity<SocialPostResponse> addComment(
            @PathVariable UUID postId,
            @RequestParam("content") String content) {
        User user = getCurrentUser();
        SocialPost post = getVisiblePostOrThrow(user, postId);
        String cleanContent = content == null ? "" : content.trim();
        if (cleanContent.isEmpty()) throw new IllegalArgumentException("Comment content is required");
        commentRepository.save(SocialPostComment.builder()
                .post(post)
                .user(user)
                .content(cleanContent)
                .build());
        SocialPostResponse response = map(post);
        broadcastPostChanged("POST_COMMENTED", postId, user.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{postId}/reactions")
    @Operation(summary = "React to a timeline post")
    public ResponseEntity<SocialPostResponse> reactPost(
            @PathVariable UUID postId,
            @RequestParam("type") String type) {
        User user = getCurrentUser();
        SocialPost post = getVisiblePostOrThrow(user, postId);
        String cleanType = type == null || type.isBlank() ? "like" : type.trim();
        SocialPostReaction reaction = reactionRepository.findByPost_IdAndUser_Id(postId, user.getId())
                .orElse(SocialPostReaction.builder().post(post).user(user).build());
        reaction.setType(cleanType);
        reactionRepository.save(reaction);
        SocialPostResponse response = map(post);
        broadcastPostChanged("POST_REACTED", postId, user.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{postId}/reactions")
    @Operation(summary = "Remove my reaction from a timeline post")
    public ResponseEntity<SocialPostResponse> removeReaction(@PathVariable UUID postId) {
        User user = getCurrentUser();
        SocialPost post = getVisiblePostOrThrow(user, postId);
        reactionRepository.findByPost_IdAndUser_Id(postId, user.getId())
                .ifPresent(reactionRepository::delete);
        SocialPostResponse response = map(post);
        broadcastPostChanged("POST_REACTION_REMOVED", postId, user.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/feed")
    @Operation(summary = "Get timeline feed (friends + self)")
    public ResponseEntity<List<SocialPostResponse>> getFeed() {
        User user = getCurrentUser();
        List<UUID> visibleUserIds = new ArrayList<>();
        visibleUserIds.add(user.getId());
        for (Friend f : friendRepository.findByUserAndStatus(user, EFriendStatus.ACCEPTED)) {
            visibleUserIds.add(f.getFriend().getId());
        }
        for (Friend f : friendRepository.findByFriendAndStatus(user, EFriendStatus.ACCEPTED)) {
            visibleUserIds.add(f.getUser().getId());
        }
        List<SocialPost> posts = postRepository.findByUser_IdInOrderByCreatedAtDesc(visibleUserIds);
        List<SocialPost> filtered = posts.stream()
                .filter(post -> canViewPost(user, post))
                .toList();
        return ResponseEntity.ok(mapPosts(filtered));
    }

    @PutMapping("/{postId}/privacy")
    @Operation(summary = "Update timeline post privacy")
    public ResponseEntity<SocialPostResponse> updatePrivacy(
            @PathVariable UUID postId,
            @RequestParam("privacy") String privacy,
            @RequestParam(value = "permittedUserIds", required = false) List<String> permittedUserIds) {
        User user = getCurrentUser();
        SocialPost post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        if (!post.getUser().getId().equals(user.getId())) throw new RuntimeException("Cannot update this post");
        post.setPrivacy(normalizePrivacy(privacy));
        post.setPermittedUserIds(joinIds(permittedUserIds));
        SocialPost saved = postRepository.save(post);
        SocialPostResponse response = map(saved);
        broadcastPostChanged("POST_PRIVACY_UPDATED", postId, user.getId());
        return ResponseEntity.ok(response);
    }

    private SocialPostResponse map(SocialPost post) {
        return mapPosts(List.of(post)).get(0);
    }

    private List<SocialPostResponse> mapPosts(List<SocialPost> posts) {
        if (posts.isEmpty()) return List.of();
        List<UUID> postIds = posts.stream().map(SocialPost::getId).toList();
        Map<UUID, List<SocialPostMedia>> mediaByPost = mediaRepository.findByPost_IdInOrderBySortOrderAsc(postIds)
                .stream()
                .collect(Collectors.groupingBy(m -> m.getPost().getId()));
        Map<UUID, List<SocialPostComment>> commentsByPost = commentRepository.findByPost_IdInOrderByCreatedAtAsc(postIds)
                .stream()
                .collect(Collectors.groupingBy(c -> c.getPost().getId()));
        Map<UUID, List<SocialPostReaction>> reactionsByPost = reactionRepository.findByPost_IdIn(postIds)
                .stream()
                .collect(Collectors.groupingBy(r -> r.getPost().getId()));

        return posts.stream().map(post -> map(post,
                mediaByPost.getOrDefault(post.getId(), List.of()),
                commentsByPost.getOrDefault(post.getId(), List.of()),
                reactionsByPost.getOrDefault(post.getId(), List.of())
        )).toList();
    }

    private SocialPostResponse map(
            SocialPost post,
            List<SocialPostMedia> mediaItems,
            List<SocialPostComment> comments,
            List<SocialPostReaction> reactions) {
        User user = post.getUser();
        List<SocialPostResponse.MediaItem> mappedMedia = mediaItems.stream()
                .map(media -> SocialPostResponse.MediaItem.builder()
                        .id(media.getId().toString())
                        .mediaUrl(minioService.ensurePublicUrl(media.getMediaUrl()))
                        .mediaType(media.getMediaType())
                        .sortOrder(media.getSortOrder())
                        .build())
                .toList();
        if (mappedMedia.isEmpty() && post.getMediaUrl() != null) {
            mappedMedia = List.of(SocialPostResponse.MediaItem.builder()
                    .id(post.getId().toString())
                    .mediaUrl(minioService.ensurePublicUrl(post.getMediaUrl()))
                    .mediaType(post.getMediaType())
                    .sortOrder(0)
                    .build());
        }
        return SocialPostResponse.builder()
                .id(post.getId().toString())
                .userId(user.getId().toString())
                .displayName(user.getDisplayName())
                .username(user.getUsername())
                .avatarUrl(minioService.ensurePublicUrl(user.getAvatarUrl()))
                .content(post.getContent())
                .mediaUrl(minioService.ensurePublicUrl(post.getMediaUrl()))
                .mediaType(post.getMediaType())
                .createdAt(post.getCreatedAt() != null ? post.getCreatedAt().atOffset(ZoneOffset.UTC).toString() : null)
                .privacy(post.getPrivacy() != null ? post.getPrivacy() : "ALL_FRIENDS")
                .permittedUserIds(splitIds(post.getPermittedUserIds()))
                .mediaItems(mappedMedia)
                .comments(comments.stream().map(this::mapComment).toList())
                .reactions(reactions.stream().map(this::mapReaction).toList())
                .build();
    }

    private SocialPostResponse.CommentItem mapComment(SocialPostComment comment) {
        User user = comment.getUser();
        return SocialPostResponse.CommentItem.builder()
                .id(comment.getId().toString())
                .userId(user.getId().toString())
                .displayName(user.getDisplayName())
                .username(user.getUsername())
                .avatarUrl(minioService.ensurePublicUrl(user.getAvatarUrl()))
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt() != null ? comment.getCreatedAt().atOffset(ZoneOffset.UTC).toString() : null)
                .build();
    }

    private SocialPostResponse.ReactionItem mapReaction(SocialPostReaction reaction) {
        User user = reaction.getUser();
        return SocialPostResponse.ReactionItem.builder()
                .id(reaction.getId().toString())
                .userId(user.getId().toString())
                .displayName(user.getDisplayName())
                .username(user.getUsername())
                .avatarUrl(minioService.ensurePublicUrl(user.getAvatarUrl()))
                .type(reaction.getType())
                .createdAt(reaction.getCreatedAt() != null ? reaction.getCreatedAt().atOffset(ZoneOffset.UTC).toString() : null)
                .build();
    }

    private SocialPost getVisiblePostOrThrow(User user, UUID postId) {
        SocialPost post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        if (post.getUser().getId().equals(user.getId())) return post;
        boolean isFriend = friendRepository.findByUserAndStatus(user, EFriendStatus.ACCEPTED).stream()
                .anyMatch(f -> f.getFriend().getId().equals(post.getUser().getId()))
                || friendRepository.findByFriendAndStatus(user, EFriendStatus.ACCEPTED).stream()
                .anyMatch(f -> f.getUser().getId().equals(post.getUser().getId()));
        if (!isFriend) throw new RuntimeException("Post not visible");
        if (!canViewPost(user, post)) throw new RuntimeException("Post not visible");
        return post;
    }

    private boolean canViewPost(User user, SocialPost post) {
        if (post.getUser().getId().equals(user.getId())) return true;
        String privacy = post.getPrivacy() != null ? post.getPrivacy() : "ALL_FRIENDS";
        List<String> permitted = splitIds(post.getPermittedUserIds());
        String currentUserId = user.getId().toString();
        if ("SPECIFIC".equals(privacy)) return permitted.contains(currentUserId);
        if ("EXCLUDE".equals(privacy)) return !permitted.contains(currentUserId);
        return true;
    }

    private String normalizePrivacy(String privacy) {
        if ("SPECIFIC".equals(privacy) || "EXCLUDE".equals(privacy)) return privacy;
        return "ALL_FRIENDS";
    }

    private String joinIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return "";
        return ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private List<String> splitIds(String ids) {
        if (ids == null || ids.isBlank()) return List.of();
        return List.of(ids.split(",")).stream()
                .filter(id -> id != null && !id.isBlank())
                .toList();
    }

    private void broadcastPostChanged(String type, UUID postId, UUID userId) {
        messagingTemplate.convertAndSend("/topic/social/posts", Map.of(
                "type", type,
                "postId", postId.toString(),
                "userId", userId.toString()
        ));
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private boolean isVideoFile(String contentType, String fileName) {
        if (contentType != null && contentType.toLowerCase().startsWith("video")) return true;
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".mp4")
                || lower.endsWith(".mov")
                || lower.endsWith(".m4v")
                || lower.endsWith(".webm")
                || lower.endsWith(".3gp");
    }
}
