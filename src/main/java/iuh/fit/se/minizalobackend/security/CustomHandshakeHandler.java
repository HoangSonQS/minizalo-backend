package iuh.fit.se.minizalobackend.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.security.Principal;
import java.util.Map;

/**
 * Custom Handshake Handler to define the WebSocket Principal.
 * It extracts the JWT from the query parameter "token" during the initial HTTP
 * Handshake.
 * The Principal's name will be set to the User's UUID, which allows signaling
 * to work
 * via messagingTemplate.convertAndSendToUser(uuid, ...).
 */
@Component
@Slf4j
public class CustomHandshakeHandler extends DefaultHandshakeHandler {

    private final JwtTokenProvider jwtTokenProvider;

    public CustomHandshakeHandler(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            String token = servletRequest.getServletRequest().getParameter("token");

            if (token != null && jwtTokenProvider.validateToken(token)) {
                String userId = jwtTokenProvider.getUserIdFromAccessToken(token);
                log.info("=== [IDENTITY-CHECK] === SUCCESS! User identified as UUID: {}. This will be the WebSocket Principal Name.", userId);

                return new StompPrincipal(userId);
            } else {
                log.error("=== [IDENTITY-CHECK] === FAILED! Token missing or invalid in URL. Realtime signaling will NOT work.");
            }
        } else {
            log.warn("=== [WS-HANDSHAKE] REJECTED === Request is not a ServletServerHttpRequest");
        }
        return null;
    }

    /**
     * Formal Principal implementation to ensure Spring's SimpUserRegistry 
     * identifies the user correctly by name.
     */
    private static class StompPrincipal implements Principal {
        private final String name;
        public StompPrincipal(String name) { this.name = name; }
        @Override public String getName() { return name; }
    }
}
