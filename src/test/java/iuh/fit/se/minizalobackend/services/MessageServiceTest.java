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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
    private final UUID messageId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        message = new Message();
        message.setId(messageId);
        message.setConversationId(conversationId);
        message.setFromUserId("user1");
        message.setToUserId("user2");
        message.setContent("Hello World");
        message.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void saveMessage_Success() {
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            if (m.getId() == null)
                m.setId(UUID.randomUUID());
            return m;
        });

        Message savedMessage = messageService.saveMessage(message);

        assertNotNull(savedMessage.getId());
        assertNotNull(savedMessage.getCreatedAt());
        verify(messageRepository, times(1)).save(message);
    }

    @Test
    void getMessages_Success() {
        Message msg1 = new Message(UUID.randomUUID(), conversationId, "u1", "u2", "Hi", LocalDateTime.now(), false);
        Message msg2 = new Message(UUID.randomUUID(), conversationId, "u2", "u1", "Hello", LocalDateTime.now(), false);
        List<Message> expectedMessages = Arrays.asList(msg1, msg2);

        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq(conversationId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(expectedMessages));

        List<Message> actualMessages = messageService.getMessages(conversationId, 0, 10);

        assertEquals(2, actualMessages.size());
        assertEquals(expectedMessages, actualMessages);
        verify(messageRepository, times(1)).findByConversationIdOrderByCreatedAtDesc(eq(conversationId),
                any(Pageable.class));
    }

    @Test
    void recallMessage_Success() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        messageService.recallMessage(messageId.toString());

        assertTrue(message.isRecalled());
        verify(messageRepository, times(1)).findById(messageId);
        verify(messageRepository, times(1)).save(message);
    }

    @Test
    void recallMessage_NotFound() {
        UUID nonExistentId = UUID.randomUUID();
        when(messageRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        messageService.recallMessage(nonExistentId.toString());

        verify(messageRepository, times(1)).findById(nonExistentId);
        verify(messageRepository, never()).save(any(Message.class));
    }
}
