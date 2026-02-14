package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.dtos.request.GroupCreationRequest;
import iuh.fit.se.minizalobackend.dtos.response.ChatRoomResponse;
import iuh.fit.se.minizalobackend.models.User;

import java.util.UUID;

public interface ChatRoomService {
    ChatRoomResponse createGroupChat(GroupCreationRequest request, User createdBy);

    ChatRoomResponse addMembersToGroup(UUID groupId, java.util.List<UUID> memberIds, User actor);

    ChatRoomResponse removeMemberFromGroup(UUID groupId, UUID memberId, User actor);

    ChatRoomResponse updateGroupInfo(UUID groupId, String newName, String newAvatarUrl, User actor);

    ChatRoomResponse changeMemberRole(UUID groupId, UUID memberId, iuh.fit.se.minizalobackend.models.ERoomRole newRole,
            User actor);

    void leaveGroup(UUID groupId, User actor);

    ChatRoomResponse getGroupChatDetails(UUID groupId);

    ChatRoomResponse createDirectChat(User user1, User user2);

    java.util.List<ChatRoomResponse> getChatRoomsForUser(User user);
}
