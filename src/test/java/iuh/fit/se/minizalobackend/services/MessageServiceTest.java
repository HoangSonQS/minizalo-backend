package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.models.Message;
import iuh.fit.se.minizalobackend.repository.MessageRepository;
import iuh.fit.se.minizalobackend.services.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private MessageServiceImpl messageService;

    private Message message;
    private final String conversationId = "user1_user2";
    private final String messageId = "msg_123";

    @BeforeEach
    void setUp() {
        message = new Message();
        message.setConversationId(conversationId);
        message.setFromUserId("user1");
        message.setToUserId("user2");
        message.setContent("Hello World");
    }

    @Test
    void saveMessage_Success() {
        doNothing().when(messageRepository).save(any(Message.class));

        Message savedMessage = messageService.saveMessage(message);

        assertNotNull(savedMessage.getMessageId());
        assertNotNull(savedMessage.getTimestamp());
        verify(messageRepository, times(1)).save(message);
    }

    @Test
    void getMessages_Success() {
        Message msg1 = new Message(conversationId, "1", "u1", "u2", "Hi", LocalDateTime.now(), false);
        Message msg2 = new Message(conversationId, "2", "u2", "u1", "Hello", LocalDateTime.now(), false);
        List<Message> expectedMessages = Arrays.asList(msg1, msg2);

        when(messageRepository.findByConversationId(conversationId)).thenReturn(expectedMessages);

        List<Message> actualMessages = messageService.getMessages(conversationId);

        assertEquals(2, actualMessages.size());
        assertEquals(expectedMessages, actualMessages);
        verify(messageRepository, times(1)).findByConversationId(conversationId);
    }

    @Test
    void recallMessage_Success() {
        message.setMessageId(messageId);
        when(messageRepository.findById(conversationId, messageId)).thenReturn(message);

        messageService.recallMessage(conversationId, messageId);

        assertTrue(message.isRecalled());
        verify(messageRepository, times(1)).findById(conversationId, messageId);
        verify(messageRepository, times(1)).save(message);
    }

    @Test
    void recallMessage_NotFound() {
        when(messageRepository.findById(conversationId, "nonexistent")).thenReturn(null);

        messageService.recallMessage(conversationId, "nonexistent");

        verify(messageRepository, times(1)).findById(conversationId, "nonexistent");
        verify(messageRepository, never()).save(any(Message.class));
    }
}
