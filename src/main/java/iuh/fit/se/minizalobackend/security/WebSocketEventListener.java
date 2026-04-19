package iuh.fit.se.minizalobackend.security;

import iuh.fit.se.minizalobackend.security.services.UserDetailsImpl;
import iuh.fit.se.minizalobackend.services.UserPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final UserPresenceService userPresenceService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        java.security.Principal principal = headerAccessor.getUser();
        
        if (principal != null) {
            String userIdStr = extractUserId(principal);
            if (userIdStr != null) {
                try {
                    userPresenceService.markUserOnline(java.util.UUID.fromString(userIdStr));
                } catch (Exception e) {
                    log.error("Failed to parse userId for online: {}", userIdStr);
                }
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        java.security.Principal principal = headerAccessor.getUser();
        
        if (principal != null) {
            String userIdStr = extractUserId(principal);
            if (userIdStr != null) {
                try {
                    userPresenceService.markUserOffline(java.util.UUID.fromString(userIdStr));
                } catch (Exception e) {
                    log.error("Failed to parse userId for offline: {}", userIdStr);
                }
            }
        }
    }

    private String extractUserId(java.security.Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken) {
            Object p = ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
            if (p instanceof UserDetailsImpl) {
                return ((UserDetailsImpl) p).getId().toString();
            }
            return p.toString();
        }
        // Case for our CustomHandshakeHandler which returns a simple Principal
        return principal.getName();
    }

    @EventListener
    public void handleSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String user = event.getUser() != null ? event.getUser().getName() : "anonymous";
        log.info("=== SIGNALING-DEBUG === User {} is subscribing to {}", user, destination);
    }
}
