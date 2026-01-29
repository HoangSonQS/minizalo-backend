package iuh.fit.se.minizalobackend.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.security.services.UserDetailsImpl;
import iuh.fit.se.minizalobackend.services.UserPresenceService;
import iuh.fit.se.minizalobackend.services.UserService;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private UserPresenceService userPresenceService;

    @MockBean
    private MinioClient minioClient;

    private UserDetailsImpl userDetails;
    private User testUser;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        userDetails = new UserDetailsImpl(
                testUserId,
                "testuser",
                "test@example.com",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        testUser = new User();
        testUser.setId(testUserId);
        testUser.setUsername("testuser");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        when(userService.getUserById(testUserId)).thenReturn(Optional.of(testUser));
    }

    @Test
    void updateFcmToken_Success() throws Exception {
        String token = "sample-fcm-token";

        doNothing().when(userService).updateFcmToken(testUserId, token);

        mockMvc.perform(put("/api/users/fcm-token")
                .contentType(MediaType.TEXT_PLAIN)
                .content(token))
                .andExpect(status().isOk());

        verify(userService, times(1)).updateFcmToken(testUserId, token);
    }

    @Test
    void getUsersStatus_Success() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        List<UUID> userIds = Arrays.asList(testUserId, otherUserId);

        when(userPresenceService.isUserOnline(testUserId)).thenReturn(true);
        when(userPresenceService.isUserOnline(otherUserId)).thenReturn(false);

        mockMvc.perform(post("/api/users/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['" + testUserId + "']").value(true))
                .andExpect(jsonPath("$.['" + otherUserId + "']").value(false));
    }
}
