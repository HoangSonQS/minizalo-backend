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

    void markAsRead(UUID groupId, User user);

    MessageResponse disbandGroup(UUID groupId, User initiator);

    List<iuh.fit.se.minizalobackend.dtos.response.GroupEventResponse> getGroupEvents(UUID groupId, User viewer);

    GroupResponse changeMemberRole(UUID groupId, UUID targetUserId, iuh.fit.se.minizalobackend.models.ERoomRole newRole, User initiator);

    // Group settings
    iuh.fit.se.minizalobackend.dtos.response.GroupSettingsResponse getGroupSettings(UUID groupId, User viewer);
    iuh.fit.se.minizalobackend.dtos.response.GroupSettingsResponse updateGroupSettings(iuh.fit.se.minizalobackend.dtos.request.UpdateGroupSettingsRequest request, User initiator);

    // Transfer ownership  
    GroupResponse transferOwnership(UUID groupId, UUID newOwnerId, User initiator);

    // Block/Unblock members
    void blockMember(UUID groupId, UUID targetUserId, User initiator);
    void unblockMember(UUID groupId, UUID targetUserId, User initiator);
    List<iuh.fit.se.minizalobackend.dtos.response.BlockedGroupMemberResponse> getBlockedMembers(UUID groupId, User viewer);

    // Join by link
    GroupResponse joinByLink(String joinToken, User user);
    String refreshJoinLink(UUID groupId, User initiator);
}