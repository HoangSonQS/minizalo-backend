package iuh.fit.se.minizalobackend.services;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

public interface QrLoginService {
    Map<String, Object> generateSession();
    SseEmitter subscribe(String sessionId);
    void confirmSession(String sessionId, String userId);
}
