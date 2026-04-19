package iuh.fit.se.minizalobackend.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Validates JWT token on STOMP CONNECT but does NOT override the Principal.
 * The Principal is set exclusively by {@link CustomHandshakeHandler} during the
 * HTTP WebSocket handshake to avoid conflicts with Spring's SimpUserRegistry
 * and user-destination resolution used by convertAndSendToUser().
 */
@Component
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    public WebSocketAuthInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (!jwtTokenProvider.validateToken(token)) {
                    throw new org.springframework.messaging.MessageDeliveryException("Invalid or expired JWT token");
                }
                log.debug("=== [WS-Auth] === STOMP CONNECT JWT validated. Principal kept from HandshakeHandler.");
            } else {
                throw new org.springframework.messaging.MessageDeliveryException("Missing Authorization header");
            }
        }
        return message;
    }
}
