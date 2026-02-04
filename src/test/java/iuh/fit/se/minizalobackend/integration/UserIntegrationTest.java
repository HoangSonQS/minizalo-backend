package iuh.fit.se.minizalobackend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.minizalobackend.dtos.request.MuteConversationRequest;
import iuh.fit.se.minizalobackend.models.*;
import iuh.fit.se.minizalobackend.payload.request.LoginRequest;
import iuh.fit.se.minizalobackend.payload.request.SignupRequest;
import iuh.fit.se.minizalobackend.payload.response.JwtResponse;
import iuh.fit.se.minizalobackend.repository.GroupRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional // Added
public class UserIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private GroupRepository groupRepository;

        @Autowired
        private RoomMemberRepository roomMemberRepository;

        @PersistenceContext
        private EntityManager entityManager;

        @MockBean
        private SimpMessagingTemplate messagingTemplate;

        // Mock Minio to avoid bean creation error if Minio is required
        @MockBean
        private io.minio.MinioClient minioClient;

        private String accessToken;
        private User testUser;
        private ChatRoom testRoom;

        @BeforeEach
        void setUp() throws Exception {
                // roomMemberRepository.deleteAll(); // Handled by @Transactional
                // groupRepository.deleteAll();
                // userRepository.deleteAll();

                // Register and Login with unique data to avoid conflicts in CI/CD
                String uniquePhone = "098765" + System.currentTimeMillis() % 10000;
                String uniqueEmail = "test" + System.currentTimeMillis() % 10000 + "@example.com";

                SignupRequest signupRequest = new SignupRequest("Test User", uniquePhone, uniqueEmail, "Password@123");
                mockMvc.perform(post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(signupRequest)))
                                .andExpect(status().isOk());

                testUser = userRepository.findByUsername(uniquePhone).orElseThrow();

                LoginRequest loginRequest = new LoginRequest(uniquePhone, "Password@123");
                MvcResult loginResult = mockMvc.perform(post("/api/auth/signin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn();

                JwtResponse jwtResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(),
                                JwtResponse.class);
                accessToken = jwtResponse.getAccessToken();

                // Create a Chat Room
                testRoom = new ChatRoom();
                testRoom.setName("Test Group");
                testRoom.setType(ERoomType.GROUP);
                testRoom.setCreatedBy(testUser);
                testRoom = groupRepository.save(testRoom);

                // Add user to room
                RoomMember member = RoomMember.builder()
                                .room(testRoom)
                                .user(testUser)
                                .role(ERoomRole.MEMBER)
                                .build();
                roomMemberRepository.save(member);

                // Flush to ensure entities are persisted
                entityManager.flush();
                entityManager.clear();
        }

        @Test
        void testMuteConversation() throws Exception {
                MuteConversationRequest request = new MuteConversationRequest();
                request.setRoomId(testRoom.getId());
                request.setMute(true);
                request.setDurationMinutes(60L);

                mockMvc.perform(post("/api/users/mute")
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk());

                // Verify DB
                RoomMember updatedMember = roomMemberRepository.findByRoomAndUser(testRoom, testUser).orElseThrow();
                assertTrue(updatedMember.isMuted());
                assertNotNull(updatedMember.getMuteUntil());
        }

        @Test
        void testHeartbeat() throws Exception {
                mockMvc.perform(post("/api/users/heartbeat")
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isOk());

                // Verify User status
                User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
                assertTrue(updatedUser.getIsOnline());
        }
}
