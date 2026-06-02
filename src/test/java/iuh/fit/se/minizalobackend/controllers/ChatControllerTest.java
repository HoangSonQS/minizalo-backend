package iuh.fit.se.minizalobackend.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.models.ChatRoom;
import iuh.fit.se.minizalobackend.models.ERoomRole;
import iuh.fit.se.minizalobackend.models.ERoomType;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.models.RoomMember;
import iuh.fit.se.minizalobackend.payload.request.RecallMessageRequest;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.services.MessageService;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MessageService messageService;

    @MockBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @MockBean
    private RoomMemberRepository roomMemberRepository;

    @MockBean(name = "internalMinioClient")
    private MinioClient minioClient;

    @MockBean(name = "publicMinioClient")
    private MinioClient publicMinioClient;

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void getChatHistory_Success() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String lastKey = "someOpaqueKey";
        int limit = 20;

        MessageDynamo message = new MessageDynamo();
        message.setChatRoomId(roomId.toString());
        message.setCreatedAt(Instant.now().toString());
        message.setContent("Hello Dynamo!");

        PaginatedMessageResult mockResult = new PaginatedMessageResult(Collections.singletonList(message),
                "nextOpaqueKey");

        when(messageService.getRoomMessages(eq(roomId), eq(lastKey), eq(limit))).thenReturn(mockResult);
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .type(ERoomType.DIRECT)
                .build();
        RoomMember member = RoomMember.builder()
                .room(room)
                .role(ERoomRole.MEMBER)
                .build();
        when(roomMemberRepository.findByRoom_IdAndUser_Id(eq(roomId), eq(userId))).thenReturn(Optional.of(member));

        mockMvc.perform(get("/api/chat/history/{roomId}", roomId)
                .param("lastKey", lastKey)
                .param("limit", String.valueOf(limit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].content").value("Hello Dynamo!"))
                .andExpect(jsonPath("$.lastEvaluatedKey").value("nextOpaqueKey"));
    }

    @Test
    @WithMockUser
    void recallMessage_Success() throws Exception {
        String roomId = UUID.randomUUID().toString();
        String messageId = UUID.randomUUID().toString();
        RecallMessageRequest recallRequest = new RecallMessageRequest();
        recallRequest.setRoomId(roomId);
        recallRequest.setMessageId(messageId);

        mockMvc.perform(post("/api/messages/recall")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(recallRequest)))
                .andExpect(status().isOk());

        verify(messageService).recallMessage(roomId, messageId, "user");
    }
}
