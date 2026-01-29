package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.dtos.response.websocket.ReadReceiptResponse;
import iuh.fit.se.minizalobackend.models.*;
import iuh.fit.se.minizalobackend.repository.GroupRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.services.impl.GroupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private GroupServiceImpl groupService;

    private User testUser;
    private ChatRoom testRoom;
    private RoomMember testMember;
    private final UUID groupId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");

        testRoom = new ChatRoom();
        testRoom.setId(groupId);
        testRoom.setType(ERoomType.GROUP);

        testMember = RoomMember.builder()
                .room(testRoom)
                .user(testUser)
                .build();
    }

    @Test
    void markAsRead_Success() {
        when(groupRepository.findByIdAndType(groupId, ERoomType.GROUP)).thenReturn(Optional.of(testRoom));
        when(roomMemberRepository.findByRoomAndUser(testRoom, testUser)).thenReturn(Optional.of(testMember));

        groupService.markAsRead(groupId, testUser);

        assertNotNull(testMember.getLastReadAt());
        verify(roomMemberRepository, times(1)).save(testMember);
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/group/" + groupId + "/read-receipts"),
                any(ReadReceiptResponse.class));
    }
}
