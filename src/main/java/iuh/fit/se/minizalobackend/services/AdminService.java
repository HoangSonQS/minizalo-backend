package iuh.fit.se.minizalobackend.services;

import java.util.List;
import java.util.Map;

public interface AdminService {
    List<Map<String, Object>> getAllUsers();
    List<Map<String, Object>> getAllRooms();
    List<Map<String, Object>> getAuditLogs(int limit);
    Map<String, Object> grantRole(String phone, String roleName);
}
