package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.dtos.request.CreateGroupRequest;
import iuh.fit.se.minizalobackend.dtos.request.SendGroupMessageRequest;
import iuh.fit.se.minizalobackend.dtos.request.UpdateGroupRequest;
import iuh.fit.se.minizalobackend.dtos.response.GroupResponse;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.payload.response.MessageResponse;

import java.util.List;
import java.util.UUID;

public interface GroupService {
    GroupResponse createGroup(CreateGroupRequest request, User creator);

    GroupResponse addMembersToGroup(UUID groupId, List<UUID> memberIds, User initiator);

    GroupResponse removeMembersFromGroup(UUID groupId, List<UUID> memberIds, User initiator);

    GroupResponse getGroupInfo(UUID groupId, User viewer);

    List<GroupResponse> getUsersGroups(User user);

    void sendGroupMessage(SendGroupMessageRequest request, User sender);

    GroupResponse updateGroup(UpdateGroupRequest request, User initiator);

    MessageResponse leaveGroup(UUID groupId, User user);
}