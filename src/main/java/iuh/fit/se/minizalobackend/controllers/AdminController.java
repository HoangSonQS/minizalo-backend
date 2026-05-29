package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.services.AdminService;
import iuh.fit.se.minizalobackend.repository.ModerationFlagRepository;
import iuh.fit.se.minizalobackend.models.ModerationFlag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.models.User;
import java.util.UUID;
import java.time.Instant;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ModerationFlagRepository moderationFlagRepository;
    private final UserRepository userRepository;
    private final iuh.fit.se.minizalobackend.services.SystemConfigService systemConfigService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/rooms")
    public ResponseEntity<List<Map<String, Object>>> getAllRooms() {
        return ResponseEntity.ok(adminService.getAllRooms());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/audit-logs")
    public ResponseEntity<List<Map<String, Object>>> getAuditLogs(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(adminService.getAuditLogs(limit));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/messages/{roomId}")
    public ResponseEntity<List<iuh.fit.se.minizalobackend.models.MessageDynamo>> getMessagesByRoom(
            @org.springframework.web.bind.annotation.PathVariable String roomId,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(adminService.getMessagesByRoom(roomId, limit));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/grant-role")
    public ResponseEntity<?> grantRole(@RequestBody Map<String, String> request) {
        try {
            String phone = request.get("phone");
            String role = request.get("role");
            if (phone == null || role == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng cung cấp 'phone' và 'role'"));
            }
            return ResponseEntity.ok(adminService.grantRole(phone, role));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/moderation")
    public ResponseEntity<List<ModerationFlag>> getModerationFlags() {
        return ResponseEntity.ok(moderationFlagRepository.findAllByStatusOrderByFlaggedAtDesc("PENDING"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/moderation/{id}/action")
    public ResponseEntity<?> handleModerationAction(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String action = request.get("action"); // APPROVE or DELETE
        return moderationFlagRepository.findById(id).map(flag -> {
            if ("DELETE".equalsIgnoreCase(action)) {
                flag.setStatus("DELETED");
                // TBD: Thực tế sẽ gọi MessageService.deleteMessage(messageId)
            } else {
                flag.setStatus("APPROVED");
            }
            moderationFlagRepository.save(flag);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/broadcast")
    public ResponseEntity<?> broadcastMessage(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nội dung không được để trống"));
        }
        adminService.broadcastMessage(content);
        return ResponseEntity.ok(Map.of("message", "Đã gửi broadcast thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.web.bind.annotation.PutMapping("/users/{userId}/lock")
    public ResponseEntity<?> lockUser(@org.springframework.web.bind.annotation.PathVariable String userId) {
        try {
            User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));
            user.setAccountLocked(true);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Đã khóa tài khoản"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.web.bind.annotation.PutMapping("/users/{userId}/unlock")
    public ResponseEntity<?> unlockUser(@org.springframework.web.bind.annotation.PathVariable String userId) {
        try {
            User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));
            user.setAccountLocked(false);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Đã mở khóa tài khoản"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/moderation/all")
    public ResponseEntity<List<ModerationFlag>> getAllPendingFlags() {
        return ResponseEntity.ok(moderationFlagRepository.findAllByStatusOrderByFlaggedAtDesc("PENDING"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/config")
    public ResponseEntity<java.util.List<iuh.fit.se.minizalobackend.models.SystemConfig>> getConfigs() {
        return ResponseEntity.ok(systemConfigService.getAll());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.web.bind.annotation.PutMapping("/config")
    public ResponseEntity<?> updateConfigs(@RequestBody Map<String, String> updates) {
        try {
            systemConfigService.saveAll(updates);
            return ResponseEntity.ok(Map.of("message", "C\u1ea5u h\u00ecnh \u0111\u00e3 \u0111\u01b0\u1ee3c c\u1eadp nh\u1eadt"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
