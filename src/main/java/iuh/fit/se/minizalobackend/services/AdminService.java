package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.models.MessageDynamo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AdminService {
    void broadcastMessage(String content);

    Map<String, Object> getDashboardSummary();

    Map<String, Object> getDashboardStorage();

    Map<String, Object> getUsers(String q, String role, Boolean locked, int page, int size);

    Map<String, Object> getUserById(UUID userId);

    List<Map<String, Object>> getUserActivities(UUID userId, int limit);

    Map<String, Object> lockUser(UUID userId, String ipAddress, String userAgent);

    Map<String, Object> unlockUser(UUID userId, String ipAddress, String userAgent);

    Map<String, Object> updateUserRoles(UUID userId, List<String> roleNames, String ipAddress, String userAgent);

    Map<String, Object> getRooms(String type, int page, int size);

    Map<String, Object> getRoomById(UUID roomId);

    List<Map<String, Object>> getRoomMembers(UUID roomId);

    Map<String, Object> getRoomStats(UUID roomId);

    List<MessageDynamo> getMessagesByRoom(String roomId, int limit);

    Map<String, Object> searchMessages(String roomId, String q, String senderId, int limit);

    Map<String, Object> hideMessage(String roomId, String messageId, String ipAddress, String userAgent);

    Map<String, Object> deleteMessage(String roomId, String messageId, String ipAddress, String userAgent);

    Map<String, Object> getGroups(int page, int size);

    Map<String, Object> getGroupById(UUID groupId);

    Map<String, Object> disbandGroup(UUID groupId, String ipAddress, String userAgent);

    Map<String, Object> getMediaStats();

    Map<String, Object> getModerationReports(String status, int page, int size);

    Map<String, Object> resolveReport(UUID reportId, String note, String ipAddress, String userAgent);

    Map<String, Object> getAuditLogs(int page, int size);

    Map<String, Object> getAuditLogById(UUID id);

    List<Map<String, Object>> getAdmins();

    List<Map<String, Object>> getRoles();

    Map<String, Object> grantRole(String phone, String roleName, String ipAddress, String userAgent);

    Map<String, Object> revokeAdminRole(UUID userId, String ipAddress, String userAgent);

    Map<String, Object> getAnalyticsUserGrowth(int days);

    Map<String, Object> getAnalyticsGroups();

    Map<String, Object> getAnalyticsTopRooms(int limit);
}
