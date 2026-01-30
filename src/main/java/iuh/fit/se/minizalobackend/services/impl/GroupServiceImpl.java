package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.dtos.request.CreateGroupRequest;
import iuh.fit.se.minizalobackend.dtos.request.SendGroupMessageRequest;
import iuh.fit.se.minizalobackend.dtos.request.UpdateGroupRequest;
import iuh.fit.se.minizalobackend.dtos.response.GroupMemberResponse;
import iuh.fit.se.minizalobackend.dtos.response.GroupResponse;
import iuh.fit.se.minizalobackend.dtos.response.websocket.GroupChatMessage;
import iuh.fit.se.minizalobackend.dtos.response.websocket.GroupEventMessage;
import iuh.fit.se.minizalobackend.dtos.response.websocket.ReadReceiptResponse;
import iuh.fit.se.minizalobackend.exception.ResourceNotFoundException;
import iuh.fit.se.minizalobackend.models.ChatRoom;
import iuh.fit.se.minizalobackend.models.ERoomEventType;
import iuh.fit.se.minizalobackend.models.ERoomRole;
import iuh.fit.se.minizalobackend.models.ERoomType;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.models.RoomMember;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.payload.response.MessageResponse;
import iuh.fit.se.minizalobackend.repository.GroupRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.GroupService;
import iuh.fit.se.minizalobackend.services.MessageService;
import iuh.fit.se.minizalobackend.utils.AppConstants;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MessageService messageService;
    private final ModelMapper modelMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, User creator) {
        // 1. Create the ChatRoom (Group)
        ChatRoom groupChatRoom = ChatRoom.builder()
                .type(ERoomType.GROUP)
                .name(request.getGroupName())
                .createdBy(creator)
                .build();

        groupChatRoom = groupRepository.save(groupChatRoom);

        // 2. Add creator as ADMIN member
        RoomMember creatorMember = RoomMember.builder()
                .room(groupChatRoom)
                .user(creator)
                .role(ERoomRole.ADMIN)
                .build();
        roomMemberRepository.save(creatorMember);

        List<RoomMember> members = new ArrayList<>();
        members.add(creatorMember);

        // 3. Add initial members if provided
        if (request.getInitialMemberIds() != null && !request.getInitialMemberIds().isEmpty()) {
            List<User> initialUsers = userRepository.findAllById(
                    request.getInitialMemberIds().stream()
                            .map(UUID::fromString)
                            .collect(Collectors.toList()));

            for (User user : initialUsers) {
                if (!user.getId().equals(creator.getId())) { // Check if user is not the creator
                    RoomMember member = RoomMember.builder()
                            .room(groupChatRoom)
                            .user(user)
                            .role(ERoomRole.MEMBER)
                            .build();
                    members.add(member);
                }
            }
            if (members.size() > 1) {
                roomMemberRepository.saveAll(members.subList(1, members.size()));
            }
        }

        // 4. Publish GROUP_CREATED event
        publishGroupEvent(groupChatRoom, ERoomEventType.CREATED,
                "Group '" + groupChatRoom.getName() + "' created.", creator);

        // 5. Build GroupResponse
        return buildGroupResponse(groupChatRoom, members);
    }

    @Override
    @Transactional
    public GroupResponse addMembersToGroup(UUID groupId, List<UUID> memberIds, User initiator) {
        ChatRoom groupChatRoom = groupRepository.findByIdAndType(groupId, ERoomType.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));

        if (!roomMemberRepository.findByRoomAndUserAndRole(groupChatRoom, initiator, ERoomRole.ADMIN).isPresent() &&
                !groupChatRoom.getCreatedBy().getId().equals(initiator.getId())) {
            throw new IllegalArgumentException("Only group admins or owner can add members.");
        }

        List<User> usersToAdd = userRepository.findAllById(memberIds);
        List<RoomMember> existingMembers = roomMemberRepository.findAllByRoom(groupChatRoom);
        List<UUID> existingMemberUserIds = existingMembers.stream()
                .map(roomMember -> roomMember.getUser().getId())
                .collect(Collectors.toList());

        List<RoomMember> newMembers = new ArrayList<>();
        for (User user : usersToAdd) {
            if (!existingMemberUserIds.contains(user.getId())) {
                RoomMember roomMember = RoomMember.builder()
                        .room(groupChatRoom)
                        .user(user)
                        .role(ERoomRole.MEMBER)
                        .build();
                newMembers.add(roomMember);
            }
        }
        roomMemberRepository.saveAll(newMembers);

        groupChatRoom.setUpdatedAt(LocalDateTime.now());
        groupRepository.save(groupChatRoom);

        // Publish MEMBER_ADDED events
        for (RoomMember member : newMembers) {
            publishGroupEvent(groupChatRoom, ERoomEventType.MEMBER_ADDED,
                    initiator.getUsername() + " added " + member.getUser().getUsername() + " to the group.",
                    member.getUser());
        }

        List<RoomMember> allMembers = roomMemberRepository.findAllByRoom(groupChatRoom);
        return buildGroupResponse(groupChatRoom, allMembers);
    }

    @Override
    @Transactional
    public GroupResponse removeMembersFromGroup(UUID groupId, List<UUID> memberIds, User initiator) {
        ChatRoom groupChatRoom = groupRepository.findByIdAndType(groupId, ERoomType.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));

        if (!roomMemberRepository.findByRoomAndUserAndRole(groupChatRoom, initiator, ERoomRole.ADMIN).isPresent() &&
                !groupChatRoom.getCreatedBy().getId().equals(initiator.getId())) {
            throw new IllegalArgumentException("Only group admins or owner can remove members.");
        }

        List<RoomMember> membersToRemove = roomMemberRepository.findByRoomAndUser_IdIn(groupChatRoom, memberIds);

        membersToRemove.removeIf(member -> member.getUser().getId().equals(groupChatRoom.getCreatedBy().getId()));

        roomMemberRepository.deleteAll(membersToRemove);

        groupChatRoom.setUpdatedAt(LocalDateTime.now());
        groupRepository.save(groupChatRoom);

        // Publish MEMBER_REMOVED events
        for (RoomMember member : membersToRemove) {
            publishGroupEvent(groupChatRoom, ERoomEventType.MEMBER_REMOVED,
                    initiator.getUsername() + " removed " + member.getUser().getUsername() + " from the group.",
                    member.getUser());
        }

        List<RoomMember> remainingMembers = roomMemberRepository.findAllByRoom(groupChatRoom);
        return buildGroupResponse(groupChatRoom, remainingMembers);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupResponse getGroupInfo(UUID groupId, User viewer) {
        ChatRoom groupChatRoom = groupRepository.findByIdAndType(groupId, ERoomType.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));

        boolean isMember = roomMemberRepository.findByRoomAndUser(groupChatRoom, viewer).isPresent();
        if (!isMember) {
            throw new IllegalArgumentException("User is not a member of this group.");
        }

        List<RoomMember> members = roomMemberRepository.findAllByRoom(groupChatRoom);
        return buildGroupResponse(groupChatRoom, members);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupResponse> getUsersGroups(User user) {
        List<RoomMember> userGroupMemberships = roomMemberRepository.findByUserAndRoom_Type(user, ERoomType.GROUP);

        return userGroupMemberships.stream()
                .map(roomMember -> {
                    ChatRoom groupChatRoom = roomMember.getRoom();
                    List<RoomMember> membersOfGroup = roomMemberRepository.findAllByRoom(groupChatRoom);
                    return buildGroupResponse(groupChatRoom, membersOfGroup);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void sendGroupMessage(SendGroupMessageRequest request, User sender) {
        ChatRoom groupChatRoom = groupRepository.findByIdAndType(request.getGroupId(), ERoomType.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + request.getGroupId()));

        boolean isMember = roomMemberRepository.findByRoomAndUser(groupChatRoom, sender).isPresent();
        if (!isMember) {
            throw new IllegalArgumentException("User is not a member of this group.");
        }

        MessageDynamo message = new MessageDynamo();
        message.setChatRoomId(groupChatRoom.getId().toString());
        message.setSenderId(sender.getId().toString());
        message.setSenderName(sender.getUsername());
        message.setContent(request.getContent());
        message.setType(AppConstants.MESSAGE_TYPE_TEXT);

        MessageDynamo savedMessage = messageService.saveMessage(message);

        // Publish GroupChatMessage to WebSocket
        GroupChatMessage groupChatMessage = GroupChatMessage.builder()
                .messageId(savedMessage.getMessageId())
                .groupId(groupChatRoom.getId().toString())
                .senderId(sender.getId().toString())
                .senderUsername(sender.getUsername())
                .content(savedMessage.getContent())
                .timestamp(savedMessage.getCreatedAt())
                .isRecalled(false)
                .build();

        String destination = "/topic/group/" + groupChatRoom.getId().toString() + "/messages";
        messagingTemplate.convertAndSend(destination, groupChatMessage);
    }

    @Override
    @Transactional
    public GroupResponse updateGroup(UpdateGroupRequest request, User initiator) {
        ChatRoom groupChatRoom = groupRepository.findByIdAndType(request.getGroupId(), ERoomType.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + request.getGroupId()));

        Optional<RoomMember> initiatorMembership = roomMemberRepository.findByRoomAndUser(groupChatRoom, initiator);
        if (initiatorMembership.isEmpty() ||
                !(initiatorMembership.get().getRole() == ERoomRole.ADMIN
                        || groupChatRoom.getCreatedBy().getId().equals(initiator.getId()))) {
            throw new IllegalArgumentException("Only group owner or admins can update group information.");
        }

        boolean changed = false;
        if (StringUtils.hasText(request.getGroupName()) && !request.getGroupName().equals(groupChatRoom.getName())) {
            String oldName = groupChatRoom.getName();
            groupChatRoom.setName(request.getGroupName());
            changed = true;
            publishGroupEvent(groupChatRoom, ERoomEventType.NAME_CHANGED,
                    initiator.getUsername() + " changed group name from '" + oldName + "' to '" + request.getGroupName()
                            + "'.",
                    null);
        }
        if (StringUtils.hasText(request.getAvatarUrl())
                && !request.getAvatarUrl().equals(groupChatRoom.getAvatarUrl())) {
            groupChatRoom.setAvatarUrl(request.getAvatarUrl());
            changed = true;
            publishGroupEvent(groupChatRoom, ERoomEventType.AVATAR_CHANGED,
                    initiator.getUsername() + " changed group avatar.", null);
        }

        if (changed) {
            groupChatRoom.setUpdatedAt(LocalDateTime.now());
            groupRepository.save(groupChatRoom);
        }

        List<RoomMember> members = roomMemberRepository.findAllByRoom(groupChatRoom);
        return buildGroupResponse(groupChatRoom, members);
    }

    @Override
    @Transactional
    public MessageResponse leaveGroup(UUID groupId, User user) {
        ChatRoom groupChatRoom = groupRepository.findByIdAndType(groupId, ERoomType.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));

        RoomMember userMembership = roomMemberRepository.findByRoomAndUser(groupChatRoom, user)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this group."));

        boolean isOwner = groupChatRoom.getCreatedBy().getId().equals(user.getId());

        if (isOwner) {
            long memberCount = roomMemberRepository.findAllByRoom(groupChatRoom).size();
            if (memberCount > 1) {
                throw new IllegalArgumentException(
                        "Group owner cannot leave a group with other members. Transfer ownership first.");
            }
        }

        roomMemberRepository.delete(userMembership);

        String responseMessage;
        if (roomMemberRepository.findAllByRoom(groupChatRoom).isEmpty()) {
            groupRepository.delete(groupChatRoom);
            responseMessage = "Group '" + groupChatRoom.getName() + "' deleted as all members left.";
            publishGroupEvent(groupChatRoom, ERoomEventType.ROOM_DELETED, responseMessage, user);
        } else {
            responseMessage = "Successfully left group '" + groupChatRoom.getName() + "'.";
            publishGroupEvent(groupChatRoom, ERoomEventType.ROOM_LEFT,
                    user.getUsername() + " has left the group.", user);
            groupChatRoom.setUpdatedAt(LocalDateTime.now());
            groupRepository.save(groupChatRoom);
        }

        return new MessageResponse(responseMessage);
    }

    @Override
    @Transactional
    public void markAsRead(UUID groupId, User user) {
        ChatRoom groupChatRoom = groupRepository.findByIdAndType(groupId, ERoomType.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));

        RoomMember member = roomMemberRepository.findByRoomAndUser(groupChatRoom, user)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this group."));

        member.setLastReadAt(LocalDateTime.now());
        roomMemberRepository.save(member);

        // Broadcast ReadReceiptResponse to WebSocket
        ReadReceiptResponse readReceipt = ReadReceiptResponse.builder()
                .groupId(groupId)
                .userId(user.getId())
                .lastReadAt(member.getLastReadAt())
                .build();

        String destination = "/topic/group/" + groupId.toString() + "/read-receipts";
        messagingTemplate.convertAndSend(destination, readReceipt);
    }

    private GroupResponse buildGroupResponse(ChatRoom chatRoom, List<RoomMember> roomMembers) {
        GroupResponse response = modelMapper.map(chatRoom, GroupResponse.class);
        response.setId(chatRoom.getId().toString());
        response.setOwnerId(chatRoom.getCreatedBy().getId().toString());

        List<GroupMemberResponse> memberResponses = roomMembers.stream()
                .map(roomMember -> {
                    GroupMemberResponse memberDto = modelMapper.map(roomMember.getUser(), GroupMemberResponse.class);
                    memberDto.setUserId(roomMember.getUser().getId().toString());
                    memberDto.setUsername(roomMember.getUser().getUsername());
                    memberDto.setAvatarUrl(roomMember.getUser().getAvatarUrl());
                    memberDto.setRole(roomMember.getRole());
                    return memberDto;
                })
                .collect(Collectors.toList());
        response.setMembers(memberResponses);
        return response;
    }

    private void publishGroupEvent(ChatRoom groupChatRoom, ERoomEventType eventType, String message,
            User affectedUser) {
        GroupEventMessage eventMessage = GroupEventMessage.builder()
                .eventId(UUID.randomUUID().toString())
                .groupId(groupChatRoom.getId().toString())
                .eventType(eventType)
                .message(message)
                .affectedUserId(affectedUser != null ? affectedUser.getId().toString() : null)
                .affectedUsername(affectedUser != null ? affectedUser.getUsername() : null)
                .timestamp(Instant.now().toString())
                .build();

        String destination = "/topic/group/" + groupChatRoom.getId().toString() + "/events";
        messagingTemplate.convertAndSend(destination, eventMessage);
    }
}