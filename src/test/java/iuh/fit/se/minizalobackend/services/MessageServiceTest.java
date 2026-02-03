package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.dtos.response.SearchMessageResponse;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.repository.MessageDynamoRepository;
import iuh.fit.se.minizalobackend.repository.GroupRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.services.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private AnalyticsService analyticsService;

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
    void searchMessages_Success() {
        UUID roomId = UUID.randomUUID();
        String query = "Hello";
        String lastKey = "lastKey";
        int limit = 10;

        SearchMessageResponse mockResponse = new SearchMessageResponse(Collections.singletonList(message), "newLastKey",
                true, 1);

        when(messageDynamoRepository.searchMessages(roomId.toString(), query, limit, lastKey))
                .thenReturn(mockResponse);

        SearchMessageResponse result = messageService.searchMessages(roomId, query, limit, lastKey);

        assertNotNull(result);
        assertEquals(1, result.getMessages().size());
        assertEquals("newLastKey", result.getLastKey());
        verify(messageDynamoRepository).searchMessages(roomId.toString(), query, limit, lastKey);
    }

    /*
     * The tests for recallMessage are commented out as the implementation
     * has been stubbed pending a full refactor for DynamoDB.
     * 
     * @Test
     * void recallMessage_Success() {
     * // Needs to be rewritten for DynamoDB (e.g., mock findById, save)
     * }
     * 
     * @Test
     * void recallMessage_NotFound() {
     * // Needs to be rewritten for DynamoDB
     * }
     * 
     * @Test
     * void recallMessage_InvalidId() {
     * // This test might still be valid depending on implementation
     * }
     */
}