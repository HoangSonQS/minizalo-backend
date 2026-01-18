package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.models.Message;
import iuh.fit.se.minizalobackend.services.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
public class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageService messageService;

    // Mock SimpMessagingTemplate to avoid actual WebSocket broker connection
    @MockBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @Test
    @WithMockUser(username = "user1")
    void getMessages_Success() throws Exception {
        String fromUser = "user1"; // Current User from Principal
        String toUser = "user2"; // Target User from RequestParam
        // Controller logic: userId1.compareTo(userId2) > 0 ? userId1 + "_" + userId2 :
        // userId2 + "_" + userId1;
        // "user1".compareTo("user2") < 0 -> returns "user2_user1"
        String conversationId = "user2_user1";

        Message msg = new Message(UUID.randomUUID(), conversationId, fromUser, toUser, "Hello", LocalDateTime.now(),
                false);
        given(messageService.getMessages(eq(conversationId), anyInt(), anyInt()))
                .willReturn(Collections.singletonList(msg));

        mockMvc.perform(get("/api/messages")
                .param("userId", toUser)
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Hello"))
                .andExpect(jsonPath("$[0].senderId").value(fromUser));
    }

    @Test
    @WithMockUser(username = "user1")
    void recallMessage_Success() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String jsonRequest = "{\"messageId\":\"" + messageId + "\"}";

        mockMvc.perform(post("/messages/recall")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isOk());

        verify(messageService).recallMessage(messageId);
    }
}
