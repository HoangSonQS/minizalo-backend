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

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final UserPresenceService userPresenceService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        UsernamePasswordAuthenticationToken userToken = (UsernamePasswordAuthenticationToken) headerAccessor.getUser();
        if (userToken != null) {
            UserDetailsImpl userDetails = (UserDetailsImpl) userToken.getPrincipal();
            userPresenceService.markUserOnline(userDetails.getId());
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        UsernamePasswordAuthenticationToken userToken = (UsernamePasswordAuthenticationToken) headerAccessor.getUser();
        if (userToken != null) {
            UserDetailsImpl userDetails = (UserDetailsImpl) userToken.getPrincipal();
            userPresenceService.markUserOffline(userDetails.getId());
        }
    }
}
