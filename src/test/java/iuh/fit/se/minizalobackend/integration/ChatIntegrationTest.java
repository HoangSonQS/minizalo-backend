package iuh.fit.se.minizalobackend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.minizalobackend.dtos.response.SearchMessageResponse;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.payload.request.LoginRequest;
import iuh.fit.se.minizalobackend.payload.request.SignupRequest;
import iuh.fit.se.minizalobackend.payload.response.JwtResponse;
import iuh.fit.se.minizalobackend.repository.MessageDynamoRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import iuh.fit.se.minizalobackend.repository.ChatRoomRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.models.ChatRoom;
import iuh.fit.se.minizalobackend.models.RoomMember;
import iuh.fit.se.minizalobackend.models.ERoomType;
import iuh.fit.se.minizalobackend.models.ERoomRole;
import iuh.fit.se.minizalobackend.models.User;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ChatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private RoomMemberRepository roomMemberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private MessageDynamoRepository messageDynamoRepository;

    // Mock Minio to avoid bean creation error if Minio is required
    @MockBean(name = "internalMinioClient")
    private io.minio.MinioClient minioClient;

    @MockBean(name = "publicMinioClient")
    private io.minio.MinioClient publicMinioClient;

    private String accessToken;
    private final UUID roomId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();

        // Register and Login
        String verToken = jwtTokenProvider.generateVerificationToken("0987654321");
        SignupRequest signupRequest = new SignupRequest("Test User", "0987654321", "test@example.com", "Password@123", null, null, verToken);
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isOk());

        LoginRequest loginRequest = new LoginRequest("0987654321", "Password@123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JwtResponse jwtResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(),
                JwtResponse.class);
        accessToken = jwtResponse.getAccessToken();
    }

    @Test
    void testSearchMessages() throws Exception {
        String query = "hello";

        MessageDynamo message = new MessageDynamo();
        message.setMessageId(UUID.randomUUID().toString());
        message.setContent("Hello world");

        SearchMessageResponse mockResponse = new SearchMessageResponse();
        mockResponse.setMessages(Collections.singletonList(message));
        mockResponse.setTotalResults(1);

        when(messageDynamoRepository.searchMessages(anyString(), eq(query), anyInt(), any(), any(), any(), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/chat/" + roomId + "/search")
                .param("q", query)
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
     }

    @Test
    void testUpdateWallpaper() throws Exception {
        User creator = userRepository.findByPhone("0987654321").orElseThrow();

        // 1. Create a DIRECT room
        ChatRoom room = chatRoomRepository.save(ChatRoom.builder()
                .type(ERoomType.DIRECT)
                .createdBy(creator)
                .build());

        // 2. Add creator as member
        roomMemberRepository.save(RoomMember.builder()
                .room(room)
                .user(creator)
                .role(ERoomRole.MEMBER)
                .build());

        // 3. Make request
        java.util.Map<String, String> requestBody = new java.util.HashMap<>();
        requestBody.put("wallpaperUrl", "minizalo-bucket/files/test.jpg");

        mockMvc.perform(put("/api/chat/rooms/" + room.getId() + "/wallpaper")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk());
    }
}
