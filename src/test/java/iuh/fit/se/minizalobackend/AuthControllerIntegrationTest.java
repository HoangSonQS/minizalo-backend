package iuh.fit.se.minizalobackend;

import org.springframework.boot.test.mock.mockito.MockBean;
import io.minio.MinioClient;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import iuh.fit.se.minizalobackend.payload.request.LoginRequest;
import iuh.fit.se.minizalobackend.payload.request.SignupRequest;
import iuh.fit.se.minizalobackend.payload.request.TokenRefreshRequest;
import iuh.fit.se.minizalobackend.payload.response.JwtResponse;
import iuh.fit.se.minizalobackend.payload.response.TokenRefreshResponse;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthControllerIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @MockBean
        private MinioClient minioClient;

        private static final String AUTH_API = "/api/auth";

        @BeforeEach
        void setUp() throws Exception {
                // Clear database before each test
                userRepository.deleteAll();

                // Mock MinioClient behavior
                when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
                doNothing().when(minioClient).makeBucket(any(MakeBucketArgs.class));
        }

        @Test
        void testUserRegistrationAndLogin() throws Exception { // 1. Register a new user
                SignupRequest signupRequest = new SignupRequest("testuser", "test@example.com", "Password@123");
                mockMvc.perform(post(AUTH_API + "/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(signupRequest)))
                                .andExpect(status().isOk())
                                .andReturn();

                assertTrue(userRepository.existsByUsername("testuser"));

                // 2. Login with the registered user
                LoginRequest loginRequest = new LoginRequest("testuser", "Password@123");
                MvcResult loginResult = mockMvc.perform(post(AUTH_API + "/signin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn();

                JwtResponse jwtResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(),
                                JwtResponse.class);
                assertNotNull(jwtResponse.getAccessToken());
                assertNotNull(jwtResponse.getRefreshToken());
                assertEquals("Bearer", jwtResponse.getTokenType());

                // 3. Test refresh token
                TokenRefreshRequest refreshRequest = new TokenRefreshRequest();
                refreshRequest.setRefreshToken(jwtResponse.getRefreshToken());

                MvcResult refreshResult = mockMvc.perform(post(AUTH_API + "/refreshtoken")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(refreshRequest)))
                                .andExpect(status().isOk())
                                .andReturn();

                TokenRefreshResponse refreshResponse = objectMapper.readValue(
                                refreshResult.getResponse().getContentAsString(), TokenRefreshResponse.class);
                assertNotNull(refreshResponse.getAccessToken());
                assertNotNull(refreshResponse.getRefreshToken());
                assertEquals("Bearer", refreshResponse.getTokenType());
                assertNotEquals(jwtResponse.getRefreshToken(), refreshResponse.getRefreshToken()); // New refresh token
                                                                                                   // should be
                                                                                                   // generated

                // 4. Test logout
                String accessToken = refreshResponse.getAccessToken(); // Use the new access token
                mockMvc.perform(post(AUTH_API + "/logout")
                                .header("Authorization", "Bearer " + accessToken))
                                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                                .andExpect(status().isOk())
                                .andReturn();

                // Try to refresh token after logout (should fail)
                TokenRefreshRequest finalRefreshRequest = new TokenRefreshRequest();
                finalRefreshRequest.setRefreshToken(refreshResponse.getRefreshToken());
                mockMvc.perform(post(AUTH_API + "/refreshtoken")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(finalRefreshRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testDuplicateUsernameRegistration() throws Exception {
                SignupRequest signupRequest = new SignupRequest("duplicateuser", "dup@example.com", "Password@123");
                mockMvc.perform(post(AUTH_API + "/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(signupRequest)))
                                .andExpect(status().isOk());

                MvcResult result = mockMvc.perform(post(AUTH_API + "/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(signupRequest)))
                                .andExpect(status().isBadRequest())
                                .andReturn();

                Map<String, Object> response = objectMapper.readValue(result.getResponse().getContentAsString(),
                                new TypeReference<Map<String, Object>>() {
                                });
                assertEquals("Error: Username is already taken!", response.get("message"));
        }

        @Test
        void testInvalidLoginCredentials() throws Exception {
                LoginRequest loginRequest = new LoginRequest("nonexistentuser", "wrongpassword");
                mockMvc.perform(post(AUTH_API + "/signin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isUnauthorized());
        }
}