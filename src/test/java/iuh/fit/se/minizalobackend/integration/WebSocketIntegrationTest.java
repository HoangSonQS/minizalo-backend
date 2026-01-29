package iuh.fit.se.minizalobackend.integration;

import iuh.fit.se.minizalobackend.dtos.request.TypingIndicatorRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
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

    private String URL;

    private WebSocketStompClient stompClient;

    @BeforeEach
    public void setup() {
        this.URL = "ws://localhost:" + port + "/ws";
        this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @Test
    public void testTypingIndicator() throws Exception {
        BlockingQueue<Object> blockingQueue = new LinkedBlockingDeque<>();

        StompHeaders connectHeaders = new StompHeaders();
        // Since we don't have a real token here in this simplified test,
        // we might need to mock or generate a valid JWT if the interceptor is active.
        // String token = jwtUtils.generateToken(someUser);
        // connectHeaders.add("Authorization", "Bearer " + token);

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
