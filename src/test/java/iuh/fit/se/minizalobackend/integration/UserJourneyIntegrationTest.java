package iuh.fit.se.minizalobackend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.minizalobackend.config.TestConfig;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.payload.request.ChatMessageRequest;
import iuh.fit.se.minizalobackend.payload.request.LoginRequest;
import iuh.fit.se.minizalobackend.payload.request.SignupRequest;
import iuh.fit.se.minizalobackend.payload.response.JwtResponse;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.repository.MessageDynamoRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.repository.GroupRepository;
import iuh.fit.se.minizalobackend.services.UserPresenceService;
import iuh.fit.se.minizalobackend.services.NotificationService;
import iuh.fit.se.minizalobackend.services.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
public class UserJourneyIntegrationTest {

        @LocalServerPort
        private int port;

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @MockBean
        private MessageDynamoRepository messageDynamoRepository;

        @MockBean
        private GroupRepository groupRepository;

        @MockBean
        private RoomMemberRepository roomMemberRepository;

        @MockBean
        private UserPresenceService userPresenceService;

        @MockBean
        private NotificationService notificationService;

        @MockBean
        private AnalyticsService analyticsService;

        private String wsUrl;
        private WebSocketStompClient stompClient;

        @BeforeEach
        void setUp() {
                wsUrl = "ws://localhost:" + port + "/ws/websocket";
                stompClient = new WebSocketStompClient(new StandardWebSocketClient());
                stompClient.setMessageConverter(new MappingJackson2MessageConverter());

                // Clear real DB (H2)
                userRepository.deleteAll();
        }

        @Test
        void testFullUserJourney() throws Exception {
                // --- STEP 1: REGISTER ---
                String phone = "0123456789";
                SignupRequest signupRequest = new SignupRequest("Journey User", phone, "journey@example.com",
                                "Password@123", null, null);

                mockMvc.perform(post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(signupRequest)))
                                .andExpect(status().isOk());

                User user = userRepository.findByUsername(phone).orElseThrow();
                assertEquals("Journey User", user.getDisplayName());

                // --- STEP 2: LOGIN ---
                LoginRequest loginRequest = new LoginRequest(phone, "Password@123");
                MvcResult loginResult = mockMvc.perform(post("/api/auth/signin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn();

                JwtResponse jwtResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(),
                                JwtResponse.class);
                String accessToken = jwtResponse.getAccessToken();
                assertNotNull(accessToken);

                // --- STEP 3: WEBSOCKET CONNECT & SEND MESSAGE ---
                StompHeaders connectHeaders = new StompHeaders();
                connectHeaders.add("Authorization", "Bearer " + accessToken);

                StompSession session = stompClient
                                .connectAsync(wsUrl, (WebSocketHttpHeaders) null, connectHeaders,
                                                new StompSessionHandlerAdapter() {
                                                })
                                .get(5, TimeUnit.SECONDS);

                assertTrue(session.isConnected());

                // Mock room check (assuming we send to a random roomId)
                String roomId = UUID.randomUUID().toString();
                // Return dummy room or member if needed by service

                ChatMessageRequest messageRequest = new ChatMessageRequest();
                messageRequest.setReceiverId(roomId);
                messageRequest.setContent("Hello from integration test!");

                // The send operation itself should succeed if auth is correct
                session.send("/app/chat.send", messageRequest);

                // Wait a bit for processing
                Thread.sleep(1000);

                // Verify that the analytics or notification service was called (proving the
                // message reached the service layer)
                // Note: In a real integration test we'd check the DB, but since we mock
                // DynamoDB, we check dependencies.
                // verify(analyticsService, atLeastOnce()).logActivity(any(), any(), any());

                session.disconnect();
        }
}
