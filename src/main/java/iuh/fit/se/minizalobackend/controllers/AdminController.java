package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.services.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ── Dashboard ──

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/dashboard/summary")
    public ResponseEntity<Map<String, Object>> getDashboardSummary() {
        return ResponseEntity.ok(adminService.getDashboardSummary());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/dashboard/storage")
    public ResponseEntity<Map<String, Object>> getDashboardStorage() {
        return ResponseEntity.ok(adminService.getDashboardStorage());
    }

    // ── Users ──

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean locked,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(adminService.getUsers(q, role, locked, page, size));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable UUID userId) {
        return ResponseEntity.ok(adminService.getUserById(userId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users/{userId}/activities")
    public ResponseEntity<List<Map<String, Object>>> getUserActivities(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(adminService.getUserActivities(userId, limit));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/users/{userId}/lock")
    public ResponseEntity<?> lockUser(@PathVariable UUID userId, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(adminService.lockUser(userId, getClientIp(request), request.getHeader("User-Agent")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/users/{userId}/unlock")
    public ResponseEntity<?> unlockUser(@PathVariable UUID userId, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(adminService.unlockUser(userId, getClientIp(request), request.getHeader("User-Agent")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/users/{userId}/roles")
    public ResponseEntity<?> updateUserRoles(
            @PathVariable UUID userId,
            @RequestBody Map<String, List<String>> body,
            HttpServletRequest request) {
        try {
            List<String> roles = body.get("roles");
            if (roles == null || roles.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng cung cấp 'roles'"));
            }
            return ResponseEntity.ok(adminService.updateUserRoles(userId, roles, getClientIp(request), request.getHeader("User-Agent")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ── Chat rooms ──

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/rooms")
    public ResponseEntity<Map<String, Object>> getRooms(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(adminService.getRooms(type, page, size));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping({"/chat/rooms/{roomId}", "/rooms/{roomId}"})
    public ResponseEntity<Map<String, Object>> getRoomById(@PathVariable UUID roomId) {
        return ResponseEntity.ok(adminService.getRoomById(roomId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping({"/chat/rooms/{roomId}/members", "/rooms/{roomId}/members"})
    public ResponseEntity<List<Map<String, Object>>> getRoomMembers(@PathVariable UUID roomId) {
        return ResponseEntity.ok(adminService.getRoomMembers(roomId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping({"/chat/rooms/{roomId}/stats", "/rooms/{roomId}/stats"})
    public ResponseEntity<Map<String, Object>> getRoomStats(@PathVariable UUID roomId) {
        return ResponseEntity.ok(adminService.getRoomStats(roomId));
    }

    // ── Messages ──

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/messages")
    public ResponseEntity<Map<String, Object>> searchMessages(
            @RequestParam String roomId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String senderId,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            return ResponseEntity.ok(adminService.searchMessages(roomId, q, senderId, limit));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/messages/{roomId}")
    public ResponseEntity<List<MessageDynamo>> getMessagesByRoom(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(adminService.getMessagesByRoom(roomId, limit));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/messages/{roomId}/{messageId}/hide")
    public ResponseEntity<?> hideMessage(
            @PathVariable String roomId,
            @PathVariable String messageId,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(adminService.hideMessage(roomId, messageId, getClientIp(request), request.getHeader("User-Agent")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/messages/{roomId}/{messageId}")
    public ResponseEntity<?> deleteMessage(
            @PathVariable String roomId,
            @PathVariable String messageId,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(adminService.deleteMessage(roomId, messageId, getClientIp(request), request.getHeader("User-Agent")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ── Groups ──

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/groups")
    public ResponseEntity<Map<String, Object>> getGroups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(adminService.getGroups(page, size));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/groups/{groupId}")
    public ResponseEntity<Map<String, Object>> getGroupById(@PathVariable UUID groupId) {
        return ResponseEntity.ok(adminService.getGroupById(groupId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/groups/{groupId}")
    public ResponseEntity<?> disbandGroup(@PathVariable UUID groupId, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(adminService.disbandGroup(groupId, getClientIp(request), request.getHeader("User-Agent")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ── Media ──

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/media/stats")
    public ResponseEntity<Map<String, Object>> getMediaStats() {
        return ResponseEntity.ok(adminService.getMediaStats());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/media")
    public ResponseEntity<Map<String, Object>> getMedia() {
        return ResponseEntity.ok(adminService.getMediaStats());
    }

    // ── Moderation ──

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/moderation/reports")
    public ResponseEntity<Map<String, Object>> getModerationReports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(adminService.getModerationReports(status, page, size));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/moderation/reports/{reportId}/resolve")
    public ResponseEntity<?> resolveReport(
            @PathVariable UUID reportId,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        try {
            String note = body != null ? body.get("note") : null;
            return ResponseEntity.ok(adminService.resolveReport(reportId, note, getClientIp(request), request.getHeader("User-Agent")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ── Audit logs ──

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/audit-logs")
    public ResponseEntity<Map<String, Object>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(adminService.getAuditLogs(page, size));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/audit-logs/{id}")
    public ResponseEntity<Map<String, Object>> getAuditLogById(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getAuditLogById(id));
    }

    // ── Admin & roles ──

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admins")
    public ResponseEntity<List<Map<String, Object>>> getAdmins() {
        return ResponseEntity.ok(adminService.getAdmins());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/roles")
    public ResponseEntity<List<Map<String, Object>>> getRoles() {
        return ResponseEntity.ok(adminService.getRoles());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/grant-role")
    public ResponseEntity<?> grantRole(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        try {
            String phone = request.get("phone");
            String role = request.get("role");
            if (phone == null || role == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng cung cấp 'phone' và 'role'"));
            }
            return ResponseEntity.ok(adminService.grantRole(phone, role, getClientIp(httpRequest), httpRequest.getHeader("User-Agent")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admins/{userId}/roles")
    public ResponseEntity<?> updateAdminRoles(
            @PathVariable UUID userId,
            @RequestBody Map<String, List<String>> body,
            HttpServletRequest request) {
        try {
            List<String> roles = body.get("roles");
            if (roles == null || roles.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng cung cấp 'roles'"));
            }
            return ResponseEntity.ok(adminService.updateUserRoles(userId, roles, getClientIp(request), request.getHeader("User-Agent")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admins/{userId}")
    public ResponseEntity<?> revokeAdminRole(@PathVariable UUID userId, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(adminService.revokeAdminRole(userId, getClientIp(request), request.getHeader("User-Agent")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ── Analytics ──

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/analytics/users/growth")
    public ResponseEntity<Map<String, Object>> getAnalyticsUserGrowth(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(adminService.getAnalyticsUserGrowth(days));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/analytics/groups")
    public ResponseEntity<Map<String, Object>> getAnalyticsGroups() {
        return ResponseEntity.ok(adminService.getAnalyticsGroups());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/analytics/messages/top-rooms")
    public ResponseEntity<Map<String, Object>> getAnalyticsTopRooms(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(adminService.getAnalyticsTopRooms(limit));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/analytics/storage")
    public ResponseEntity<Map<String, Object>> getAnalyticsStorage() {
        return ResponseEntity.ok(adminService.getDashboardStorage());
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
