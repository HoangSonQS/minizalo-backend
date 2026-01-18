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
        String fromUser = "user1";
        String toUser = "user2";
        // Controller logic: userId1.compareTo(userId2) > 0 ? userId1 + "_" + userId2 :
        // userId2 + "_" + userId1;
        // "user1".compareTo("user2") < 0 -> returns "user2_user1"
        String conversationId = "user2_user1";

        Message msg = new Message(conversationId, "1", fromUser, toUser, "Hello", LocalDateTime.now(), false);
        given(messageService.getMessages(conversationId)).willReturn(Collections.singletonList(msg));

        mockMvc.perform(get("/messages/{from}/{to}", fromUser, toUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Hello"))
                .andExpect(jsonPath("$[0].fromUserId").value(fromUser));
    }

    @Test
    @WithMockUser(username = "user1")
    void recallMessage_Success() throws Exception {
        String jsonRequest = "{\"fromUserId\":\"user1\", \"toUserId\":\"user2\", \"messageId\":\"msg123\"}";
        String conversationId = "user2_user1";

        mockMvc.perform(post("/messages/recall")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isOk());

        verify(messageService).recallMessage(conversationId, "msg123");
    }
}
