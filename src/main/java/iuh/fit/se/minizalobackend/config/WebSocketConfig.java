package iuh.fit.se.minizalobackend.config;

import iuh.fit.se.minizalobackend.security.CustomHandshakeHandler;
import iuh.fit.se.minizalobackend.security.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final CustomHandshakeHandler customHandshakeHandler;

    public WebSocketConfig(WebSocketAuthInterceptor webSocketAuthInterceptor, 
                           CustomHandshakeHandler customHandshakeHandler) {
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
        this.customHandshakeHandler = customHandshakeHandler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // User destinations (/user/queue/...) are resolved by Spring to broker destinations under /queue.
        // Therefore the simple broker must enable "/queue" (not "/user") to deliver per-user messages.
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS endpoint
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(customHandshakeHandler)
                .withSockJS();

        // Raw WebSocket endpoint
        registry.addEndpoint("/ws-raw")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(customHandshakeHandler);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
