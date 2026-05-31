package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.models.AdminAuditLog;
import iuh.fit.se.minizalobackend.models.ChatRoom;
import iuh.fit.se.minizalobackend.models.ContentReport;
import iuh.fit.se.minizalobackend.models.EReportStatus;
import iuh.fit.se.minizalobackend.models.ERole;
import iuh.fit.se.minizalobackend.models.ERoomType;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.models.Role;
import iuh.fit.se.minizalobackend.models.RoomMember;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.models.UserActivity;
import iuh.fit.se.minizalobackend.repository.AdminAuditLogRepository;
import iuh.fit.se.minizalobackend.repository.ChatRoomRepository;
import iuh.fit.se.minizalobackend.repository.ContentReportRepository;
import iuh.fit.se.minizalobackend.repository.GroupRepository;
import iuh.fit.se.minizalobackend.repository.MessageDynamoRepository;
import iuh.fit.se.minizalobackend.repository.RefreshTokenRepository;
import iuh.fit.se.minizalobackend.repository.RoleRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.repository.UserActivityRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.repository.MessageDynamoRepository;
import iuh.fit.se.minizalobackend.models.RoomMember;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.models.ERoomType;
import iuh.fit.se.minizalobackend.services.AdminService;
import iuh.fit.se.minizalobackend.services.MessageService;
import iuh.fit.se.minizalobackend.security.services.UserDetailsImpl;
import iuh.fit.se.minizalobackend.services.AdminService;
import iuh.fit.se.minizalobackend.services.AnalyticsService;
import iuh.fit.se.minizalobackend.services.MessageService;
import iuh.fit.se.minizalobackend.utils.AppConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final GroupRepository groupRepository;
    private final UserActivityRepository userActivityRepository;
    private final RoleRepository roleRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MessageDynamoRepository messageDynamoRepository;
    private final MessageService messageService;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final ContentReportRepository contentReportRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AnalyticsService analyticsService;
    private final MessageService messageService;
    private final GroupRoomCleanupService groupRoomCleanupService;

    @Override
    public Map<String, Object> getDashboardSummary() {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalUsers", userRepository.count());
        summary.put("lockedUsers", userRepository.countByAccountLockedTrue());
        summary.put("totalRooms", chatRoomRepository.count());
        summary.put("totalGroups", chatRoomRepository.countByType(ERoomType.GROUP));
        summary.put("totalDirectChats", chatRoomRepository.countByType(ERoomType.DIRECT));
        summary.put("totalCloudRooms", chatRoomRepository.countByType(ERoomType.CLOUD));
        summary.put("pendingReports", contentReportRepository.countByStatus(EReportStatus.PENDING));
        summary.put("messagesLast30Days", userActivityRepository.countByActivityTypeAndTimestampAfter(
                AppConstants.ACTIVITY_MESSAGE_SENT, since));
        summary.put("activeUsersLast24h", analyticsService.getActiveUserStats(10).get("currentActiveUsers"));
        summary.put("since", since.toString());
        return summary;
    }

    @Override
    public Map<String, Object> getDashboardStorage() {
        Map<String, Object> storage = new HashMap<>();
        storage.put("provider", "MinIO");
        storage.put("uploadEndpoint", "/api/files/upload");
        storage.put("presignedEndpoint", "/api/media/presigned-url");
        storage.put("avatarEndpoint", "/api/users/avatar");
        storage.put("status", "active");
        storage.put("note", "Dung lượng chi tiết cần metadata file riêng; hiện trả về cấu hình endpoint.");
        return storage;
    }

    @Override
    public Map<String, Object> getUsers(String q, String role, Boolean locked, int page, int size) {
        Page<User> userPage = userRepository.adminSearchUsers(q, locked, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        List<Map<String, Object>> content = userPage.getContent().stream()
                .map(this::mapUserSummary)
                .filter(map -> role == null || role.isBlank() || role.equals(map.get("role")))
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("page", userPage.getNumber());
        result.put("size", userPage.getSize());
        result.put("totalElements", userPage.getTotalElements());
        result.put("totalPages", userPage.getTotalPages());
        return result;
    }

    @Override
    public Map<String, Object> getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        Map<String, Object> map = mapUserSummary(user);
        map.put("phone", user.getPhone());
        map.put("displayName", user.getDisplayName());
        map.put("avatarUrl", user.getAvatarUrl());
        map.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        map.put("updatedAt", user.getUpdatedAt() != null ? user.getUpdatedAt().toString() : null);
        map.put("lastSeen", user.getLastSeen() != null ? user.getLastSeen().toString() : null);
        map.put("roles", user.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.toList()));
        return map;
    }

    @Override
    public List<Map<String, Object>> getUserActivities(UUID userId, int limit) {
        return userActivityRepository.findByUser_IdOrderByTimestampDesc(userId).stream()
                .limit(limit)
                .map(this::mapUserActivity)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> lockUser(UUID userId, String ipAddress, String userAgent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        if (Boolean.TRUE.equals(user.getAccountLocked())) {
            throw new RuntimeException("Tài khoản đã bị khóa");
        }
        user.setAccountLocked(true);
        user.setIsOnline(false);
        userRepository.save(user);
        refreshTokenRepository.deleteByUser(user);
        analyticsService.logActivity(getCurrentAdminId(), AppConstants.ACTIVITY_ADMIN_USER_LOCKED, "Locked user: " + userId);
        logAdminAction("LOCK_USER", "USER", userId.toString(), "locked=false", "locked=true", ipAddress, userAgent, "SUCCESS");
        return Map.of("success", true, "message", "Đã khóa tài khoản " + user.getUsername());
    }

    @Override
    public Map<String, Object> unlockUser(UUID userId, String ipAddress, String userAgent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        user.setAccountLocked(false);
        userRepository.save(user);
        analyticsService.logActivity(getCurrentAdminId(), AppConstants.ACTIVITY_ADMIN_USER_UNLOCKED, "Unlocked user: " + userId);
        logAdminAction("UNLOCK_USER", "USER", userId.toString(), "locked=true", "locked=false", ipAddress, userAgent, "SUCCESS");
        return Map.of("success", true, "message", "Đã mở khóa tài khoản " + user.getUsername());
    }

    @Override
    public Map<String, Object> updateUserRoles(UUID userId, List<String> roleNames, String ipAddress, String userAgent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        String before = user.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.joining(","));
        Set<Role> newRoles = new HashSet<>();
        for (String roleName : roleNames) {
            ERole eRole = ERole.valueOf(roleName);
            Role role = roleRepository.findByName(eRole).orElseGet(() -> roleRepository.save(new Role(null, eRole)));
            newRoles.add(role);
        }
        user.setRoles(newRoles);
        userRepository.save(user);
        String after = newRoles.stream().map(r -> r.getName().name()).collect(Collectors.joining(","));
        logAdminAction("UPDATE_USER_ROLES", "USER", userId.toString(), before, after, ipAddress, userAgent, "SUCCESS");
        return Map.of("success", true, "message", "Đã cập nhật quyền cho " + user.getUsername(), "roles", roleNames);
    }

    @Override
    public Map<String, Object> getRooms(String type, int page, int size) {
        List<ChatRoom> allRooms = chatRoomRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"));
        if (type != null && !type.isBlank()) {
            ERoomType roomType = ERoomType.valueOf(type.toUpperCase());
            allRooms = allRooms.stream().filter(r -> r.getType() == roomType).collect(Collectors.toList());
        }
        int from = Math.min(page * size, allRooms.size());
        int to = Math.min(from + size, allRooms.size());
        List<Map<String, Object>> content = allRooms.subList(from, to).stream()
                .map(this::mapRoomSummary)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("page", page);
        result.put("size", size);
        result.put("totalElements", allRooms.size());
        result.put("totalPages", size == 0 ? 0 : (int) Math.ceil((double) allRooms.size() / size));
        return result;
    }

    @Override
    public Map<String, Object> getRoomById(UUID roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng chat"));
        Map<String, Object> map = mapRoomSummary(room);
        map.put("description", room.getDescription());
        map.put("avatarUrl", room.getAvatarUrl());
        map.put("createdAt", room.getCreatedAt() != null ? room.getCreatedAt().toString() : null);
        map.put("createdBy", room.getCreatedBy() != null ? room.getCreatedBy().getUsername() : null);
        return map;
    }

    @Override
    public List<Map<String, Object>> getRoomMembers(UUID roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng chat"));
        return roomMemberRepository.findAllByRoomWithUsersFetched(room).stream()
                .map(member -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("userId", member.getUser().getId().toString());
                    map.put("username", member.getUser().getUsername());
                    map.put("displayName", member.getUser().getDisplayName());
                    map.put("role", member.getRole().name());
                    map.put("joinedAt", member.getJoinedAt() != null ? member.getJoinedAt().toString() : null);
                    return map;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getRoomStats(UUID roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng chat"));
        long messagesCount = userActivityRepository.countByActivityTypeAndDetailsContaining(
                AppConstants.ACTIVITY_MESSAGE_SENT, roomId.toString());
        long memberCount = roomMemberRepository.countByRoom(room);
        Map<String, Object> stats = new HashMap<>();
        stats.put("roomId", roomId.toString());
        stats.put("memberCount", memberCount);
        stats.put("messagesCount", messagesCount);
        stats.put("type", room.getType().name());
        return stats;
    }

    @Override
    public List<MessageDynamo> getMessagesByRoom(String roomId, int limit) {
        return messageDynamoRepository.getMessagesByRoomId(roomId, null, limit).getMessages();
    }

    @Override
    public Map<String, Object> searchMessages(String roomId, String q, String senderId, int limit) {
        if (roomId == null || roomId.isBlank()) {
            throw new RuntimeException("roomId là bắt buộc");
        }
        var response = messageDynamoRepository.searchMessages(roomId, q, limit, null, senderId, null, null);
        Map<String, Object> result = new HashMap<>();
        result.put("messages", response.getMessages());
        result.put("total", response.getMessages() != null ? response.getMessages().size() : 0);
        return result;
    }

    @Override
    public Map<String, Object> hideMessage(String roomId, String messageId, String ipAddress, String userAgent) {
        MessageDynamo message = messageDynamoRepository.getMessage(roomId, messageId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tin nhắn"));
        String before = message.getContent();
        message.setRecalled(true);
        message.setRecalledAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        message.setContent("[Tin nhắn đã bị ẩn bởi quản trị viên]");
        messageDynamoRepository.save(message);
        analyticsService.logActivity(getCurrentAdminId(), AppConstants.ACTIVITY_ADMIN_MESSAGE_HIDDEN,
                "Hidden message " + messageId + " in room " + roomId);
        logAdminAction("HIDE_MESSAGE", "MESSAGE", messageId, before, message.getContent(), ipAddress, userAgent, "SUCCESS");
        return Map.of("success", true, "message", "Đã ẩn tin nhắn");
    }

    @Override
    public Map<String, Object> deleteMessage(String roomId, String messageId, String ipAddress, String userAgent) {
        boolean deleted = messageDynamoRepository.deleteByMessageId(roomId, messageId);
        if (!deleted) {
            throw new RuntimeException("Không tìm thấy tin nhắn để xóa");
        }
        analyticsService.logActivity(getCurrentAdminId(), AppConstants.ACTIVITY_ADMIN_MESSAGE_DELETED,
                "Deleted message " + messageId + " in room " + roomId);
        logAdminAction("DELETE_MESSAGE", "MESSAGE", messageId, null, null, ipAddress, userAgent, "SUCCESS");
        return Map.of("success", true, "message", "Đã xóa tin nhắn");
    }

    @Override
    public Map<String, Object> getGroups(int page, int size) {
        List<ChatRoom> groups = groupRepository.findByType(ERoomType.GROUP);
        groups.sort(Comparator.comparing(ChatRoom::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        int from = Math.min(page * size, groups.size());
        int to = Math.min(from + size, groups.size());
        List<Map<String, Object>> content = groups.subList(from, to).stream()
                .map(this::mapRoomSummary)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("page", page);
        result.put("size", size);
        result.put("totalElements", groups.size());
        result.put("totalPages", size == 0 ? 0 : (int) Math.ceil((double) groups.size() / size));
        return result;
    }

    @Override
    public Map<String, Object> getGroupById(UUID groupId) {
        ChatRoom group = groupRepository.findByIdAndType(groupId, ERoomType.GROUP)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhóm"));
        return getRoomById(group.getId());
    }

    @Override
    public Map<String, Object> disbandGroup(UUID groupId, String ipAddress, String userAgent) {
        ChatRoom group = groupRepository.findByIdAndType(groupId, ERoomType.GROUP)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhóm"));
        groupRoomCleanupService.hardDeleteGroup(group);
        analyticsService.logActivity(getCurrentAdminId(), AppConstants.ACTIVITY_ADMIN_GROUP_DISBANDED,
                "Disbanded group: " + groupId);
        logAdminAction("DISBAND_GROUP", "GROUP", groupId.toString(), group.getName(), null, ipAddress, userAgent, "SUCCESS");
        return Map.of("success", true, "message", "Đã giải tán nhóm");
    }

    @Override
    public Map<String, Object> getMediaStats() {
        return getDashboardStorage();
    }

    @Override
    public Map<String, Object> getModerationReports(String status, int page, int size) {
        Page<ContentReport> reportPage;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (status != null && !status.isBlank()) {
            reportPage = contentReportRepository.findByStatusOrderByCreatedAtDesc(EReportStatus.valueOf(status.toUpperCase()), pageable);
        } else {
            reportPage = contentReportRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        List<Map<String, Object>> content = reportPage.getContent().stream().map(report -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", report.getId().toString());
            map.put("reporter", report.getReporter() != null ? report.getReporter().getUsername() : "Unknown");
            map.put("targetType", report.getTargetType().name());
            map.put("targetId", report.getTargetId());
            map.put("reason", report.getReason());
            map.put("details", report.getDetails());
            map.put("status", report.getStatus().name());
            map.put("createdAt", report.getCreatedAt() != null ? report.getCreatedAt().toString() : null);
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("page", reportPage.getNumber());
        result.put("size", reportPage.getSize());
        result.put("totalElements", reportPage.getTotalElements());
        result.put("totalPages", reportPage.getTotalPages());
        return result;
    }

    @Override
    public Map<String, Object> resolveReport(UUID reportId, String note, String ipAddress, String userAgent) {
        ContentReport report = contentReportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy báo cáo"));
        report.setStatus(EReportStatus.RESOLVED);
        report.setResolutionNote(note);
        report.setResolvedBy(getCurrentAdminId());
        report.setResolvedAt(LocalDateTime.now());
        contentReportRepository.save(report);
        analyticsService.logActivity(getCurrentAdminId(), AppConstants.ACTIVITY_ADMIN_REPORT_RESOLVED,
                "Resolved report: " + reportId);
        logAdminAction("RESOLVE_REPORT", "REPORT", reportId.toString(), "PENDING", "RESOLVED", ipAddress, userAgent, "SUCCESS");
        return Map.of("success", true, "message", "Đã xử lý báo cáo");
    }

    @Override
    public Map<String, Object> getAuditLogs(int page, int size) {
        Page<AdminAuditLog> logPage = adminAuditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        List<Map<String, Object>> adminLogs = logPage.getContent().stream().map(this::mapAdminAuditLog).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", adminLogs);
        result.put("page", logPage.getNumber());
        result.put("size", logPage.getSize());
        result.put("totalElements", logPage.getTotalElements());
        result.put("totalPages", logPage.getTotalPages());
        return result;
    }

    @Override
    public Map<String, Object> getAuditLogById(UUID id) {
        AdminAuditLog log = adminAuditLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy audit log"));
        return mapAdminAuditLog(log);
    }

    @Override
    public List<Map<String, Object>> getAdmins() {
        return userRepository.findAllAdmins().stream().map(user -> {
            Map<String, Object> map = mapUserSummary(user);
            map.put("phone", user.getPhone());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getRoles() {
        return roleRepository.findAll().stream().map(role -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", role.getName().name());
            map.put("id", role.getId() != null ? role.getId().toString() : null);
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> grantRole(String phone, String roleName, String ipAddress, String userAgent) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với SĐT: " + phone));

        ERole eRole;
        try {
            eRole = ERole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Quyền không hợp lệ: " + roleName);
        }

        Role role = roleRepository.findByName(eRole)
                .orElseGet(() -> roleRepository.save(new Role(null, eRole)));

        if (user.getRoles().contains(role)) {
            throw new RuntimeException("Người dùng đã có quyền này.");
        }

        user.getRoles().add(role);
        userRepository.save(user);
        analyticsService.logActivity(getCurrentAdminId(), AppConstants.ACTIVITY_ADMIN_ROLE_GRANTED,
                "Granted " + roleName + " to " + phone);
        logAdminAction("GRANT_ROLE", "USER", user.getId().toString(), null, roleName, ipAddress, userAgent, "SUCCESS");

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Đã cấp quyền " + roleName + " cho SĐT " + phone);
        return response;
    }

    @Override
    public void broadcastMessage(String content) {
        List<ChatRoom> rooms = chatRoomRepository.findAll();
        for (ChatRoom room : rooms) {
            if (room.getType() == ERoomType.GROUP || room.getType() == ERoomType.CLOUD) {
                MessageDynamo msg = new MessageDynamo();
                msg.setMessageId(java.util.UUID.randomUUID().toString());
                msg.setChatRoomId(room.getId().toString());
                msg.setSenderId("SYSTEM");
                msg.setSenderName("Hệ thống MiniZalo");
                msg.setContent(content);
                msg.setType("TEXT");
                msg.setCreatedAt(java.time.Instant.now().toString());
                messageService.saveMessage(msg);
            }
        }
    public Map<String, Object> revokeAdminRole(UUID userId, String ipAddress, String userAgent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        UUID currentAdminId = getCurrentAdminId();
        if (user.getId().equals(currentAdminId)) {
            throw new RuntimeException("Không thể thu hồi quyền admin của chính mình");
        }
        Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                .orElseThrow(() -> new RuntimeException("ROLE_ADMIN chưa được khởi tạo"));
        if (!user.getRoles().contains(adminRole)) {
            throw new RuntimeException("Người dùng không có quyền admin");
        }
        user.getRoles().remove(adminRole);
        userRepository.save(user);
        analyticsService.logActivity(currentAdminId, AppConstants.ACTIVITY_ADMIN_ROLE_REVOKED,
                "Revoked ROLE_ADMIN from " + userId);
        logAdminAction("REVOKE_ADMIN", "USER", userId.toString(), "ROLE_ADMIN", null, ipAddress, userAgent, "SUCCESS");
        return Map.of("success", true, "message", "Đã thu hồi quyền admin");
    }

    @Override
    public Map<String, Object> getAnalyticsUserGrowth(int days) {
        return analyticsService.getUserGrowthStats(LocalDateTime.now().minusDays(days));
    }

    @Override
    public Map<String, Object> getAnalyticsGroups() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalGroups", chatRoomRepository.countByType(ERoomType.GROUP));
        stats.put("totalDirect", chatRoomRepository.countByType(ERoomType.DIRECT));
        stats.put("totalCloud", chatRoomRepository.countByType(ERoomType.CLOUD));
        return stats;
    }

    @Override
    public Map<String, Object> getAnalyticsTopRooms(int limit) {
        List<Map<String, Object>> rooms = chatRoomRepository.findAll().stream()
                .map(room -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("roomId", room.getId().toString());
                    map.put("name", room.getName());
                    map.put("type", room.getType().name());
                    map.put("messages", userActivityRepository.countByActivityTypeAndDetailsContaining(
                            AppConstants.ACTIVITY_MESSAGE_SENT, room.getId().toString()));
                    return map;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("messages"), (Long) a.get("messages")))
                .limit(limit)
                .collect(Collectors.toList());
        return Map.of("topRooms", rooms);
    }

    private Map<String, Object> mapUserSummary(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId().toString());
        map.put("name", user.getUsername());
        map.put("email", user.getEmail());
        map.put("phone", user.getPhone());
        boolean isAdmin = user.getRoles().stream().anyMatch(r -> r.getName() == ERole.ROLE_ADMIN);
        String role = isAdmin ? ERole.ROLE_ADMIN.name() : user.getRoles().stream()
                .map(r -> r.getName().name())
                .findFirst()
                .orElse(ERole.ROLE_USER.name());
        map.put("role", role);
        String state = Boolean.TRUE.equals(user.getAccountLocked()) ? "Locked"
                : Boolean.TRUE.equals(user.getIsOnline()) ? "Online" : "Active";
        map.put("state", state);
        map.put("accountLocked", user.getAccountLocked());
        map.put("messages", userActivityRepository.findByUser_IdOrderByTimestampDesc(user.getId()).stream()
                .filter(a -> AppConstants.ACTIVITY_MESSAGE_SENT.equals(a.getActivityType()))
                .count());
        return map;
    }

    private Map<String, Object> mapRoomSummary(ChatRoom room) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", room.getId().toString());
        List<RoomMember> members = roomMemberRepository.findAllByRoomWithUsersFetched(room);
        map.put("members", members.size());
        String name = room.getName();
        if (name == null || name.isBlank()) {
            if (!members.isEmpty()) {
                name = members.stream()
                        .map(m -> {
                            String display = m.getUser().getDisplayName();
                            return display != null && !display.isBlank() ? display : m.getUser().getUsername();
                        })
                        .collect(Collectors.joining(", "));
            } else {
                name = "Direct Chat";
            }
        }
        map.put("name", name);
        map.put("type", room.getType().name());
        long messagesCount = userActivityRepository.countByActivityTypeAndDetailsContaining(
                AppConstants.ACTIVITY_MESSAGE_SENT, room.getId().toString());
        map.put("messages", messagesCount);
        map.put("updatedAt", room.getUpdatedAt() != null ? room.getUpdatedAt().toString() : "");
        return map;
    }

    private Map<String, Object> mapUserActivity(UserActivity activity) {
        Map<String, Object> map = new HashMap<>();
        map.put("time", activity.getTimestamp().toString());
        map.put("actor", activity.getUser() != null ? activity.getUser().getUsername() : "System");
        map.put("action", activity.getActivityType());
        map.put("target", activity.getDetails());
        map.put("status", "Hoàn tất");
        return map;
    }

    private Map<String, Object> mapAdminAuditLog(AdminAuditLog log) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", log.getId().toString());
        map.put("time", log.getCreatedAt().toString());
        map.put("actorAdminId", log.getActorAdminId().toString());
        map.put("action", log.getAction());
        map.put("targetType", log.getTargetType());
        map.put("target", log.getTargetId());
        map.put("beforeData", log.getBeforeData());
        map.put("afterData", log.getAfterData());
        map.put("status", log.getStatus());
        return map;
    }

    private void logAdminAction(String action, String targetType, String targetId,
                                String beforeData, String afterData,
                                String ipAddress, String userAgent, String status) {
        AdminAuditLog log = AdminAuditLog.builder()
                .actorAdminId(getCurrentAdminId())
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .beforeData(beforeData)
                .afterData(afterData)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
        adminAuditLogRepository.save(log);
    }

    private UUID getCurrentAdminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl details) {
            return details.getId();
        }
        throw new RuntimeException("Không xác định được admin hiện tại");
    }
}
