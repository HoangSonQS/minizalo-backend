package iuh.fit.se.minizalobackend.services;

import java.util.Map;

public interface QrLoginService {
    Map<String, Object> generateSession();
    Map<String, Object> getSessionStatus(String sessionId);
    void confirmSession(String sessionId, String userId);
}
