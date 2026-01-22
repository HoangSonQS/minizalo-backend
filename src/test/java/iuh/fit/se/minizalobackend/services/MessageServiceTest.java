package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.repository.MessageDynamoRepository;
import iuh.fit.se.minizalobackend.services.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageDynamoRepository messageDynamoRepository;

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
        message.setSenderId("user1");
        message.setContent("Hello World");
        // CreatedAt is set in the service
    }

    @Test
    void saveMessage_Success() {
        // The service sets the timestamp and ID if null, so we pass the object
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