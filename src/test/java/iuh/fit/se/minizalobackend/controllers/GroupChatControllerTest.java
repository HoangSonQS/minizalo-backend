package iuh.fit.se.minizalobackend.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.minizalobackend.dtos.request.*;
import iuh.fit.se.minizalobackend.dtos.response.GroupResponse;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.payload.response.MessageResponse;
import iuh.fit.se.minizalobackend.security.services.UserDetailsImpl;
import iuh.fit.se.minizalobackend.services.GroupService;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class GroupChatControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private GroupService groupService;

        @MockBean
        private UserService userService;

        @MockBean
        private MinioClient minioClient;

        private UserDetailsImpl userDetails;
        private User testUser;
        private UUID testUserId;

        @BeforeEach
        void setUp() {
                testUserId = UUID.randomUUID();
                userDetails = new UserDetailsImpl(testUserId, "testuser", "test@example.com", "password",
                                new ArrayList<>());
                testUser = new User();
                testUser.setId(testUserId);
                testUser.setUsername("testuser");
                testUser.setDisplayName("Test User");
                testUser.setPassword("password");
                testUser.setEmail("test@example.com");

                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(userDetails, null,
                                                userDetails.getAuthorities()));

                when(userService.getUserById(testUserId)).thenReturn(Optional.of(testUser));
        }

        @Test
        void createGroup_Success() throws Exception {
                List<String> initialMemberIds = new ArrayList<>();
                CreateGroupRequest createGroupRequest = new CreateGroupRequest("Test Group", initialMemberIds);
                GroupResponse groupResponse = new GroupResponse(UUID.randomUUID().toString(), "Test Group",
                                testUserId.toString(), null, new ArrayList<>());

                when(groupService.createGroup(any(CreateGroupRequest.class), eq(testUser))).thenReturn(groupResponse);

                mockMvc.perform(post("/api/group")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createGroupRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.groupName").value("Test Group"))
                                .andExpect(jsonPath("$.ownerId").value(testUserId.toString()));
        }

        @Test
        void createGroup_InvalidRequest_ReturnsBadRequest() throws Exception {
                CreateGroupRequest createGroupRequest = new CreateGroupRequest("", new ArrayList<>());

                mockMvc.perform(post("/api/group")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createGroupRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void addMembers_Success() throws Exception {
                UUID groupId = UUID.randomUUID();
                List<UUID> memberIds = Collections.singletonList(UUID.randomUUID());
                AddMembersRequest addMembersRequest = new AddMembersRequest(groupId, memberIds);
                GroupResponse groupResponse = new GroupResponse(groupId.toString(), "Test Group", testUserId.toString(),
                                null,
                                new ArrayList<>());

                when(groupService.addMembersToGroup(eq(groupId), eq(memberIds), eq(testUser)))
                                .thenReturn(groupResponse);

                mockMvc.perform(post("/api/group/members")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(addMembersRequest)))
                                .andExpect(status().isOk());
        }

        @Test
        void removeMembers_Success() throws Exception {
                UUID groupId = UUID.randomUUID();
                List<UUID> memberIds = Collections.singletonList(UUID.randomUUID());
                RemoveMembersRequest removeMembersRequest = new RemoveMembersRequest(groupId, memberIds);
                GroupResponse groupResponse = new GroupResponse(groupId.toString(), "Test Group", testUserId.toString(),
                                null,
                                new ArrayList<>());

                when(groupService.removeMembersFromGroup(eq(groupId), eq(memberIds), eq(testUser)))
                                .thenReturn(groupResponse);

                mockMvc.perform(delete("/api/group/members")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(removeMembersRequest)))
                                .andExpect(status().isOk());
        }

        @Test
        void getGroupInfo_Success() throws Exception {
                UUID groupId = UUID.randomUUID();
                GroupResponse groupResponse = new GroupResponse(groupId.toString(), "Test Group", testUserId.toString(),
                                null,
                                new ArrayList<>());

                when(groupService.getGroupInfo(eq(groupId), eq(testUser))).thenReturn(groupResponse);

                mockMvc.perform(get("/api/group/{groupId}", groupId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.groupName").value("Test Group"));
        }

        @Test
        void getUsersGroups_Success() throws Exception {
                UUID groupId = UUID.randomUUID();
                GroupResponse groupResponse = new GroupResponse(groupId.toString(), "Test Group", testUserId.toString(),
                                null,
                                new ArrayList<>());
                List<GroupResponse> groupList = Collections.singletonList(groupResponse);

                when(groupService.getUsersGroups(eq(testUser))).thenReturn(groupList);

                mockMvc.perform(get("/api/group/my-groups"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].groupName").value("Test Group"));
        }

        @Test
        void sendGroupMessage_Success() throws Exception {
                UUID groupId = UUID.randomUUID();
                SendGroupMessageRequest sendMessageRequest = new SendGroupMessageRequest(groupId, "Hello World");

                // Service now returns void
                doNothing().when(groupService).sendGroupMessage(any(SendGroupMessageRequest.class), eq(testUser));

                mockMvc.perform(post("/api/group/message")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(sendMessageRequest)))
                                .andExpect(status().isOk());
        }

        @Test
        void updateGroup_Success() throws Exception {
                UUID groupId = UUID.randomUUID();
                UpdateGroupRequest updateGroupRequest = new UpdateGroupRequest(groupId, "New Group Name", null);
                GroupResponse groupResponse = new GroupResponse(groupId.toString(), "New Group Name",
                                testUserId.toString(),
                                null, new ArrayList<>());

                when(groupService.updateGroup(any(UpdateGroupRequest.class), eq(testUser))).thenReturn(groupResponse);

                mockMvc.perform(put("/api/group")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateGroupRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.groupName").value("New Group Name"));
        }

        @Test
        void leaveGroup_Success() throws Exception {
                UUID groupId = UUID.randomUUID();
                MessageResponse messageResponse = new MessageResponse("Successfully left group");

                when(groupService.leaveGroup(eq(groupId), eq(testUser))).thenReturn(messageResponse);

                mockMvc.perform(post("/api/group/leave/{groupId}", groupId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Successfully left group"));
        }

        @Test
        void markAsRead_Success() throws Exception {
                UUID groupId = UUID.randomUUID();
                MarkReadRequest markReadRequest = new MarkReadRequest(groupId);

                doNothing().when(groupService).markAsRead(eq(groupId), eq(testUser));

                mockMvc.perform(post("/api/group/read-receipt")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(markReadRequest)))
                                .andExpect(status().isOk());
        }
}
