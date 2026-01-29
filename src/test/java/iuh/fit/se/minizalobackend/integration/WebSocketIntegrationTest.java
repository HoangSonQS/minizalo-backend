package iuh.fit.se.minizalobackend.integration;

import iuh.fit.se.minizalobackend.dtos.request.TypingIndicatorRequest;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    private String URL;

    private WebSocketStompClient stompClient;

    @BeforeEach
    public void setup() {
        this.URL = "ws://localhost:" + port + "/ws";
        this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        // Mock a user for the interceptor
        UUID userId = UUID.fromString("7927515e-6531-487d-8153-6591739c9f0b");
        User user = new User();
        user.setId(userId);
        user.setUsername("testuser");
        user.setPassword("password");

        org.mockito.Mockito.when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
    }

    @Test
    public void testTypingIndicator() throws Exception {
        BlockingQueue<Object> blockingQueue = new LinkedBlockingDeque<>();

        StompHeaders connectHeaders = new StompHeaders();
        // Generate a valid test token
        String token = jwtTokenProvider.generateAccessToken("7927515e-6531-487d-8153-6591739c9f0b");
        connectHeaders.add("Authorization", "Bearer " + token);

        StompSession session = stompClient
                .connectAsync(URL, (WebSocketHttpHeaders) null, connectHeaders, new StompSessionHandlerAdapter() {
                })
                .get(1, TimeUnit.SECONDS);

        String roomId = UUID.randomUUID().toString();
        session.subscribe("/topic/typing/" + roomId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Object.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                blockingQueue.add(payload);
            }
        });

        TypingIndicatorRequest request = TypingIndicatorRequest.builder()
                .roomId(roomId)
                .isTyping(true)
                .build();

        session.send("/app/chat.typing", request);

        Object receivedPayload = blockingQueue.poll(5, TimeUnit.SECONDS);
        assertNotNull(receivedPayload);
    }
}
