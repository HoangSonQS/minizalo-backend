package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.dtos.response.SearchMessageResponse;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.repository.MessageDynamoRepository;
import iuh.fit.se.minizalobackend.repository.GroupRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageDynamoRepository messageDynamoRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private UserPresenceService userPresenceService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MessageServiceImpl messageService;

    private MessageDynamo message;
    private final String chatRoomId = UUID.randomUUID().toString();
    private final String messageId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        message = new MessageDynamo();
        message.setMessageId(messageId);
        message.setChatRoomId(chatRoomId);
        message.setSenderId(UUID.randomUUID().toString());
        message.setSenderName("Test User");
        message.setContent("Hello World");
        message.setCreatedAt(Instant.now().toString());
        message.setReadBy(new ArrayList<>());
    }

    @Test
    void saveMessage_Success() {
        lenient().when(groupRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        MessageDynamo savedMessage = messageService.saveMessage(message);

        assertNotNull(savedMessage.getCreatedAt());
        assertNotNull(savedMessage.getMessageId());
        verify(messageDynamoRepository, times(1)).save(any(MessageDynamo.class));
    }

    @Test
    void getRoomMessages_Success() {
        UUID roomId = UUID.randomUUID();
        String lastKey = "someKey";
        int limit = 20;

        MessageDynamo dynamoMessage = new MessageDynamo();
        dynamoMessage.setContent("Hello from Dynamo");
        PaginatedMessageResult expectedResult = new PaginatedMessageResult(Collections.singletonList(dynamoMessage),
                "nextKey");

        when(messageDynamoRepository.getMessagesByRoomId(roomId.toString(), lastKey, limit)).thenReturn(expectedResult);

        PaginatedMessageResult actualResult = messageService.getRoomMessages(roomId, lastKey, limit);

        assertEquals(1, actualResult.getMessages().size());
        assertEquals("Hello from Dynamo", actualResult.getMessages().get(0).getContent());
        assertEquals("nextKey", actualResult.getLastEvaluatedKey());
        verify(messageDynamoRepository, times(1)).getMessagesByRoomId(roomId.toString(), lastKey, limit);
    }

    @Test
    void getPinnedMessages_Success() {
        UUID roomId = UUID.randomUUID();
        String lastKey = "someKey";
        int limit = 20;

        MessageDynamo pinned = new MessageDynamo();
        pinned.setChatRoomId(roomId.toString());
        pinned.setMessageId(UUID.randomUUID().toString());
        pinned.setPinned(true);
        pinned.setCreatedAt(Instant.now().toString());
        PaginatedMessageResult expectedResult = new PaginatedMessageResult(Collections.singletonList(pinned),
                "nextKey");

        when(messageDynamoRepository.getPinnedMessagesByRoomId(roomId.toString(), lastKey, limit))
                .thenReturn(expectedResult);

        PaginatedMessageResult actualResult = messageService.getPinnedMessages(roomId, lastKey, limit);

        assertEquals(1, actualResult.getMessages().size());
        assertEquals("nextKey", actualResult.getLastEvaluatedKey());
        verify(messageDynamoRepository, times(1)).getPinnedMessagesByRoomId(roomId.toString(), lastKey, limit);
    }

    @Test
    void searchMessages_Success() {
        UUID roomId = UUID.randomUUID();
        String query = "Hello";
        String lastKey = "lastKey";
        int limit = 10;

        SearchMessageResponse mockResponse = new SearchMessageResponse(Collections.singletonList(message), "newLastKey",
                true, 1);

        when(messageDynamoRepository.searchMessages(roomId.toString(), query, limit, lastKey, null, null, null))
                .thenReturn(mockResponse);

        SearchMessageResponse result = messageService.searchMessages(roomId, query, limit, lastKey, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.getMessages().size());
        assertEquals("newLastKey", result.getLastKey());
        verify(messageDynamoRepository).searchMessages(roomId.toString(), query, limit, lastKey, null, null, null);
    }

    @Test
    void recallMessage_OnlySenderAllowed() {
        String requesterId = UUID.randomUUID().toString();
        message.setSenderId(UUID.randomUUID().toString()); // different sender
        message.setCreatedAt(Instant.now().toString());
        when(messageDynamoRepository.getMessage(chatRoomId, messageId)).thenReturn(Optional.of(message));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> messageService.recallMessage(chatRoomId, messageId, requesterId));
        assertTrue(ex.getMessage().toLowerCase().contains("only sender"));
        verify(messageDynamoRepository, never()).save(any(MessageDynamo.class));
    }

    @Test
    void pinMessage_LimitFivePinnedMessages() {
        message.setPinned(false);
        when(messageDynamoRepository.getMessage(chatRoomId, messageId)).thenReturn(Optional.of(message));
        when(messageDynamoRepository.countPinnedMessages(chatRoomId)).thenReturn(5L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> messageService.pinMessage(chatRoomId, messageId, true));
        assertTrue(ex.getMessage().contains("tối đa 5"));
        verify(messageDynamoRepository, never()).save(any(MessageDynamo.class));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void pinMessage_AlreadyPinned_ShouldNotBlock() {
        message.setPinned(true);
        when(messageDynamoRepository.getMessage(chatRoomId, messageId)).thenReturn(Optional.of(message));

        assertDoesNotThrow(() -> messageService.pinMessage(chatRoomId, messageId, true));
        verify(messageDynamoRepository, times(2)).save(any(MessageDynamo.class));
        verify(messagingTemplate, times(1)).convertAndSend(contains("/topic/chat/" + chatRoomId + "/pin"),
                any(Object.class));
    }

    @Test
    void removeReaction_RemovesAndBroadcasts() {
        message.setReactions(new ArrayList<>());
        message.getReactions().add(iuh.fit.se.minizalobackend.models.MessageReaction.builder()
                .userId("u1")
                .emoji("👍")
                .build());
        when(messageDynamoRepository.getMessage(chatRoomId, messageId)).thenReturn(Optional.of(message));

        assertDoesNotThrow(() -> messageService.removeReaction(chatRoomId, messageId, "u1"));

        verify(messageDynamoRepository, times(1)).save(any(MessageDynamo.class));
        verify(messagingTemplate, times(1)).convertAndSend(contains("/topic/chat/" + chatRoomId + "/reaction"),
                any(Object.class));
    }
}