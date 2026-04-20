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
import iuh.fit.se.minizalobackend.models.GroupEvent;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.models.RoomMember;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.payload.response.MessageResponse;
import iuh.fit.se.minizalobackend.repository.BlockedGroupMemberRepository;
import iuh.fit.se.minizalobackend.repository.GroupEventRepository;
import iuh.fit.se.minizalobackend.repository.GroupRepository;
import iuh.fit.se.minizalobackend.repository.GroupSettingsRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.GroupService;
import iuh.fit.se.minizalobackend.services.MessageService;
import iuh.fit.se.minizalobackend.utils.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final GroupEventRepository groupEventRepository;
    private final GroupSettingsRepository groupSettingsRepository;
    private final BlockedGroupMemberRepository blockedGroupMemberRepository;
    private final MessageService messageService;
    private final ModelMapper modelMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final iuh.fit.se.minizalobackend.services.MinioService minioService;

    private String displayNameOf(User u) {
        if (u == null) return "Ai đó";
        if (u.getDisplayName() != null && !u.getDisplayName().trim().isEmpty()) return u.getDisplayName().trim();
        if (u.getUsername() != null && !u.getUsername().trim().isEmpty()) return u.getUsername().trim();
        return "Ai đó";
    }

    private void publishSystemChatMessage(ChatRoom room, User initiator, String sysMsg) {
        try {
            final String roomIdStr = room.getId().toString();
            final String initiatorDisplayName = displayNameOf(initiator);

            MessageDynamo message = new MessageDynamo();
            message.setChatRoomId(roomIdStr);
            message.setSenderId(initiator.getId().toString());
            message.setSenderName(initiatorDisplayName);
            message.setContent(sysMsg);
            message.setType(AppConstants.MESSAGE_TYPE_SYSTEM);
            MessageDynamo savedMessage = messageService.saveMessage(message);

            GroupChatMessage groupChatMessage = GroupChatMessage.builder()
                    .messageId(savedMessage.getMessageId())
                    .groupId(roomIdStr)
                    .senderId(initiator.getId().toString())
                    .senderUsername(initiatorDisplayName)
                    .content(sysMsg)
                    .type(AppConstants.MESSAGE_TYPE_SYSTEM)
                    .timestamp(savedMessage.getCreatedAt())
                    .isRecalled(false)
                    .build();

            messagingTemplate.convertAndSend("/topic/chat/" + roomIdStr, groupChatMessage);
        } catch (Exception e) {
            log.warn("Failed to publish SYSTEM chat message: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, User creator) {
        // 1. Create the ChatRoom (Group)
        ChatRoom groupChatRoom = ChatRoom.builder()
                .type(ERoomType.GROUP)
                .name(request.getGroupName())
                .createdBy(creator)
                .build();

        final ChatRoom savedRoom = groupRepository.save(groupChatRoom);
        final String roomIdStr = savedRoom.getId().toString();

        // 1.5 Create default GroupSettings
        iuh.fit.se.minizalobackend.models.GroupSettings settings = iuh.fit.se.minizalobackend.models.GroupSettings.builder()
                .group(savedRoom)
                .joinLink(UUID.randomUUID().toString())
                .build();
        groupSettingsRepository.save(settings);

        // 2. Add creator as ADMIN member
        RoomMember creatorMember = RoomMember.builder()
                .room(savedRoom)
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
                            .room(savedRoom)
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

        // 3.5 SYSTEM trong cuộc hội thoại (giống addMembers) + broadcast WebSocket
        String creatorDisplayName = creator.getDisplayName() != null && !creator.getDisplayName().trim().isEmpty()
                ? creator.getDisplayName()
                : creator.getUsername();
        boolean addedAnyMember = false;
        for (RoomMember member : members) {
            if (member.getUser().getId().equals(creator.getId())) {
                continue;
            }
            addedAnyMember = true;
            User addedUser = member.getUser();
            String memberDisplayName = addedUser.getDisplayName() != null
                    && !addedUser.getDisplayName().trim().isEmpty()
                    ? addedUser.getDisplayName()
                    : addedUser.getUsername();
            String sysMsg = creatorDisplayName + " đã thêm " + memberDisplayName + " vào nhóm.";

            MessageDynamo message = new MessageDynamo();
            message.setChatRoomId(savedRoom.getId().toString());
            message.setSenderId(creator.getId().toString());
            message.setSenderName(creatorDisplayName);
            message.setContent(sysMsg);
            message.setType(AppConstants.MESSAGE_TYPE_SYSTEM);
            MessageDynamo savedMessage = messageService.saveMessage(message);

            GroupChatMessage groupChatMessage = GroupChatMessage.builder()
                    .messageId(savedMessage.getMessageId())
                    .groupId(savedRoom.getId().toString())
                    .senderId(creator.getId().toString())
                    .senderUsername(creatorDisplayName)
                    .content(sysMsg)
                    .type(AppConstants.MESSAGE_TYPE_SYSTEM)
                    .timestamp(savedMessage.getCreatedAt())
                    .isRecalled(false)
                    .build();

            messagingTemplate.convertAndSend("/topic/chat/" + roomIdStr, groupChatMessage);

            publishGroupEvent(savedRoom, ERoomEventType.MEMBER_ADDED, sysMsg, addedUser);
        }

        if (!addedAnyMember) {
            String sysMsg = creatorDisplayName + " đã tạo nhóm \"" + savedRoom.getName() + "\".";
            MessageDynamo message = new MessageDynamo();
            message.setChatRoomId(savedRoom.getId().toString());
            message.setSenderId(creator.getId().toString());
            message.setSenderName(creatorDisplayName);
            message.setContent(sysMsg);
            message.setType(AppConstants.MESSAGE_TYPE_SYSTEM);
            MessageDynamo savedMessage = messageService.saveMessage(message);

            GroupChatMessage groupChatMessage = GroupChatMessage.builder()
                    .messageId(savedMessage.getMessageId())
                    .groupId(savedRoom.getId().toString())
                    .senderId(creator.getId().toString())
                    .senderUsername(creatorDisplayName)
                    .content(sysMsg)
                    .type(AppConstants.MESSAGE_TYPE_SYSTEM)
                    .timestamp(savedMessage.getCreatedAt())
                    .isRecalled(false)
                    .build();

            messagingTemplate.convertAndSend("/topic/chat/" + roomIdStr, groupChatMessage);
        }

        savedRoom.setUpdatedAt(LocalDateTime.now());
        groupRepository.save(savedRoom);

        // 4. Publish GROUP_CREATED event
        publishGroupEvent(savedRoom, ERoomEventType.CREATED,
                "Group '" + savedRoom.getName() + "' created.", creator);

        // Notify creator + initial members to refresh room list (realtime)
        try {
            messagingTemplate.convertAndSendToUser(
                    creator.getUsername(),
                    "/queue/rooms",
                    "{\"action\":\"ADDED\",\"roomId\":\"" + roomIdStr + "\"}"
            );
            if (request.getInitialMemberIds() != null) {
                for (String mid : request.getInitialMemberIds()) {
                    try {
                        UUID uid = UUID.fromString(mid);
                        if (uid.equals(creator.getId())) continue;
                        userRepository.findById(uid).ifPresent(u ->
                                messagingTemplate.convertAndSendToUser(u.getUsername(), "/queue/rooms",
                                        "{\"action\":\"ADDED\",\"roomId\":\"" + roomIdStr + "\"}")
                        );
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            log.warn("Failed to push room ADDED updates: {}", e.getMessage());
        }

        try {
            messagingTemplate.convertAndSend(
                    "/topic/chat/" + roomIdStr,
                    "{\"roomListEvent\":\"ADDED\",\"roomId\":\"" + roomIdStr + "\"}");
        } catch (Exception e) {
            log.warn("Failed to broadcast room ADDED on chat topic: {}", e.getMessage());
        }

        // 5. Build GroupResponse
        return buildGroupResponse(savedRoom, members);
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

        // Không cho thêm user đã bị chặn khỏi nhóm (chỉ có thể thêm lại sau khi bỏ chặn)
        List<UUID> blockedIds = memberIds.stream()
                .filter(uid -> blockedGroupMemberRepository.existsByGroupIdAndBlockedUserId(groupId, uid))
                .toList();
        if (!blockedIds.isEmpty()) {
            throw new IllegalArgumentException("Không thể thêm thành viên đã bị chặn khỏi nhóm. Vui lòng bỏ chặn trước.");
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

        // Publish MEMBER_ADDED events and SYSTEM message
        for (RoomMember member : newMembers) {
            String initiatorName = initiator.getDisplayName() != null && !initiator.getDisplayName().trim().isEmpty() ? initiator.getDisplayName() : initiator.getUsername();
            String memberName = member.getUser().getDisplayName() != null && !member.getUser().getDisplayName().trim().isEmpty() ? member.getUser().getDisplayName() : member.getUser().getUsername();
            String sysMsg = initiatorName + " đã thêm " + memberName + " vào nhóm.";

            MessageDynamo message = new MessageDynamo();
            message.setChatRoomId(groupChatRoom.getId().toString());
            message.setSenderId(initiator.getId().toString());
            message.setSenderName(initiatorName);
            message.setContent(sysMsg);
            message.setType(AppConstants.MESSAGE_TYPE_SYSTEM);
            MessageDynamo savedMessage = messageService.saveMessage(message);

            GroupChatMessage groupChatMessage = GroupChatMessage.builder()
                    .messageId(savedMessage.getMessageId())
                    .groupId(groupChatRoom.getId().toString())
                    .senderId(initiator.getId().toString())
                    .senderUsername(initiatorName)
                    .content(sysMsg)
                    .type(AppConstants.MESSAGE_TYPE_SYSTEM)
                    .timestamp(savedMessage.getCreatedAt())
                    .isRecalled(false)
                    .build();

            messagingTemplate.convertAndSend("/topic/chat/" + groupChatRoom.getId().toString(), groupChatMessage);

            publishGroupEvent(groupChatRoom, ERoomEventType.MEMBER_ADDED,
                    sysMsg,
                    member.getUser());

            // Notify the added member to refresh room list (realtime)
            try {
                messagingTemplate.convertAndSendToUser(
                        member.getUser().getUsername(),
                        "/queue/rooms",
                        "{\"action\":\"ADDED\",\"roomId\":\"" + groupChatRoom.getId().toString() + "\"}"
                );
            } catch (Exception e) {
                log.warn("Failed to push room ADDED for user {}: {}", member.getUser().getUsername(), e.getMessage());
            }
        }

        if (!newMembers.isEmpty()) {
            try {
                messagingTemplate.convertAndSend(
                        "/topic/chat/" + groupChatRoom.getId().toString(),
                        "{\"roomListEvent\":\"ADDED\",\"roomId\":\"" + groupChatRoom.getId().toString() + "\"}");
            } catch (Exception e) {
                log.warn("Failed to broadcast room ADDED on chat topic: {}", e.getMessage());
            }
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

        // Publish MEMBER_REMOVED events and SYSTEM message
        for (RoomMember member : membersToRemove) {
            String initiatorName = initiator.getDisplayName() != null && !initiator.getDisplayName().trim().isEmpty() ? initiator.getDisplayName() : initiator.getUsername();
            String memberName = member.getUser().getDisplayName() != null && !member.getUser().getDisplayName().trim().isEmpty() ? member.getUser().getDisplayName() : member.getUser().getUsername();
            String sysMsg = initiatorName + " đã xóa " + memberName + " khỏi nhóm.";
            
            MessageDynamo message = new MessageDynamo();
            message.setChatRoomId(groupChatRoom.getId().toString());
            message.setSenderId(initiator.getId().toString());
            message.setSenderName(initiatorName);
            message.setContent(sysMsg);
            message.setType(AppConstants.MESSAGE_TYPE_SYSTEM);
            MessageDynamo savedMessage = messageService.saveMessage(message);

            GroupChatMessage groupChatMessage = GroupChatMessage.builder()
                    .messageId(savedMessage.getMessageId())
                    .groupId(groupChatRoom.getId().toString())
                    .senderId(initiator.getId().toString())
                    .senderUsername(initiatorName)
                    .content(sysMsg)
                    .type(AppConstants.MESSAGE_TYPE_SYSTEM)
                    .timestamp(savedMessage.getCreatedAt())
                    .isRecalled(false)
                    .build();

            messagingTemplate.convertAndSend("/topic/chat/" + groupChatRoom.getId().toString(), groupChatMessage);

            try {
                messagingTemplate.convertAndSend(
                        "/topic/chat/" + groupChatRoom.getId().toString(),
                        "{\"roomListEvent\":\"REMOVED\",\"roomId\":\"" + groupChatRoom.getId()
                                + "\",\"forUserId\":\"" + member.getUser().getId() + "\"}");
            } catch (Exception e) {
                log.warn("Failed to broadcast room REMOVED for kicked user: {}", e.getMessage());
            }

            publishGroupEvent(groupChatRoom, ERoomEventType.MEMBER_REMOVED,
                    sysMsg,
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

        RoomMember member = roomMemberRepository.findByRoomAndUser(groupChatRoom, sender)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this group."));

        iuh.fit.se.minizalobackend.models.GroupSettings settings = groupSettingsRepository.findByGroupId(groupChatRoom.getId()).orElse(null);
        if (settings != null && !settings.isAllowMemberSendMessage()) {
            boolean isOwner = groupChatRoom.getCreatedBy().getId().equals(sender.getId());
            boolean isAdmin = member.getRole() == ERoomRole.ADMIN;
            if (!isOwner && !isAdmin) {
                throw new IllegalArgumentException("Only admins can send messages in this group.");
            }
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
                .type(savedMessage.getType())
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

        RoomMember initiatorMembership = roomMemberRepository.findByRoomAndUser(groupChatRoom, initiator)
                .orElseThrow(() -> new IllegalArgumentException("Only group members can update group information."));

        iuh.fit.se.minizalobackend.models.GroupSettings settings = groupSettingsRepository.findByGroupId(groupChatRoom.getId()).orElse(null);
        if (settings != null && !settings.isAllowMemberChangeName()) {
            boolean isOwner = groupChatRoom.getCreatedBy().getId().equals(initiator.getId());
            boolean isAdmin = initiatorMembership.getRole() == ERoomRole.ADMIN;
            if (!isOwner && !isAdmin) {
                throw new IllegalArgumentException("Only admins can change group name and avatar.");
            }
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
            String userName = user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty() ? user.getDisplayName() : user.getUsername();
            String sysMsg = userName + " đã rời nhóm.";
            
            MessageDynamo message = new MessageDynamo();
            message.setChatRoomId(groupChatRoom.getId().toString());
            message.setSenderId(user.getId().toString());
            message.setSenderName(userName);
            message.setContent(sysMsg);
            message.setType(AppConstants.MESSAGE_TYPE_SYSTEM);
            MessageDynamo savedMessage = messageService.saveMessage(message);

            GroupChatMessage groupChatMessage = GroupChatMessage.builder()
                    .messageId(savedMessage.getMessageId())
                    .groupId(groupChatRoom.getId().toString())
                    .senderId(user.getId().toString())
                    .senderUsername(userName)
                    .content(sysMsg)
                    .type(AppConstants.MESSAGE_TYPE_SYSTEM)
                    .timestamp(savedMessage.getCreatedAt())
                    .isRecalled(false)
                    .build();

            messagingTemplate.convertAndSend("/topic/chat/" + groupChatRoom.getId().toString(), groupChatMessage);

            publishGroupEvent(groupChatRoom, ERoomEventType.ROOM_LEFT,
                    sysMsg,
                    user);
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

    @Override
    public List<iuh.fit.se.minizalobackend.dtos.response.GroupEventResponse> getGroupEvents(UUID groupId, User viewer) {
        // Validate user is member
        if (!roomMemberRepository.existsByRoom_IdAndUser_Id(groupId, viewer.getId())) {
            throw new IllegalArgumentException("You are not a member of this group");
        }

        List<GroupEvent> events = groupEventRepository.findByGroupIdOrderByCreatedAtDesc(groupId);

        return events.stream().map(event -> iuh.fit.se.minizalobackend.dtos.response.GroupEventResponse.builder()
                .id(event.getId())
                .groupId(event.getGroup().getId())
                .userId(event.getUser() != null ? event.getUser().getId() : null)
                .userName(event.getUser() != null ? event.getUser().getDisplayName() : "System")
                .userAvatar(event.getUser() != null ? minioService.ensurePublicUrl(event.getUser().getAvatarUrl()) : null)
                .eventType(event.getEventType())
                .metadata(event.getMetadata())
                .createdAt(event.getCreatedAt())
                .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MessageResponse disbandGroup(UUID groupId, User initiator) {
        ChatRoom groupChatRoom = groupRepository.findByIdAndType(groupId, ERoomType.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));

        if (!groupChatRoom.getCreatedBy().getId().equals(initiator.getId())) {
            throw new IllegalArgumentException("Only the group owner can disband the group.");
        }

        publishGroupEvent(groupChatRoom, ERoomEventType.ROOM_DELETED,
                "Group '" + groupChatRoom.getName() + "' has been disbanded by the owner.", initiator);

        // Capture members before deletion to push realtime updates
        List<RoomMember> membersToNotify = roomMemberRepository.findAllByRoom(groupChatRoom);

        // Mọi thành viên đã subscribe /topic/chat/{roomId} — broadcast trước khi xóa DB (ổn định hơn /user/queue)
        try {
            messagingTemplate.convertAndSend(
                    "/topic/chat/" + groupId.toString(),
                    "{\"roomListEvent\":\"REMOVED\",\"roomId\":\"" + groupId.toString() + "\"}");
        } catch (Exception e) {
            log.warn("Failed to broadcast room REMOVED on chat topic: {}", e.getMessage());
        }

        // Xóa các bảng phụ trước để tránh lỗi FK constraint (vd: blocked_group_members → chat_rooms)
        messageService.deleteAllMessages(groupChatRoom.getId().toString());
        blockedGroupMemberRepository.deleteAll(blockedGroupMemberRepository.findByGroup(groupChatRoom));
        groupSettingsRepository.findByGroupId(groupId).ifPresent(groupSettingsRepository::delete);
        roomMemberRepository.deleteAll(roomMemberRepository.findAllByRoom(groupChatRoom));
        groupEventRepository.deleteAll(groupEventRepository.findByGroupIdOrderByCreatedAtDesc(groupId));
        groupRepository.delete(groupChatRoom);

        // Push ROOM_REMOVED to all members (realtime remove from chat list)
        try {
            for (RoomMember rm : membersToNotify) {
                User u = rm.getUser();
                if (u == null) continue;
                messagingTemplate.convertAndSendToUser(
                        u.getUsername(),
                        "/queue/rooms",
                        "{\"action\":\"REMOVED\",\"roomId\":\"" + groupId.toString() + "\"}"
                );
            }
            messagingTemplate.convertAndSendToUser(
                    initiator.getUsername(),
                    "/queue/rooms",
                    "{\"action\":\"REMOVED\",\"roomId\":\"" + groupId.toString() + "\"}"
            );
        } catch (Exception e) {
            log.warn("Failed to push room REMOVED updates: {}", e.getMessage());
        }

        return new MessageResponse("Group disbanded successfully.");
    }

    private GroupResponse buildGroupResponse(ChatRoom chatRoom, List<RoomMember> roomMembers) {
        GroupResponse response = modelMapper.map(chatRoom, GroupResponse.class);
        response.setId(chatRoom.getId().toString());
        response.setGroupName(chatRoom.getName());
        response.setAvatarUrl(minioService.ensurePublicUrl(chatRoom.getAvatarUrl()));
        response.setOwnerId(chatRoom.getCreatedBy().getId().toString());

        List<GroupMemberResponse> memberResponses = roomMembers.stream()
                .map(roomMember -> {
                    GroupMemberResponse memberDto = modelMapper.map(roomMember.getUser(), GroupMemberResponse.class);
                    memberDto.setUserId(roomMember.getUser().getId().toString());
                    memberDto.setUsername(roomMember.getUser().getUsername());
                    memberDto.setDisplayName(roomMember.getUser().getDisplayName());
                    memberDto.setAvatarUrl(minioService.ensurePublicUrl(roomMember.getUser().getAvatarUrl()));
                    memberDto.setRole(roomMember.getRole());
                    return memberDto;
                })
                .collect(Collectors.toList());
        response.setMembers(memberResponses);

        groupSettingsRepository.findByGroupId(chatRoom.getId()).ifPresent(settings -> {
            iuh.fit.se.minizalobackend.dtos.response.GroupSettingsResponse settingsResponse = modelMapper.map(settings, iuh.fit.se.minizalobackend.dtos.response.GroupSettingsResponse.class);
            response.setSettings(settingsResponse);
        });

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

    @Override
    @Transactional
    public GroupResponse changeMemberRole(UUID groupId, UUID targetUserId, ERoomRole newRole, User initiator) {
        ChatRoom groupChatRoom = groupRepository.findByIdAndType(groupId, ERoomType.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));

        // Only the owner (creator) can change roles
        if (!groupChatRoom.getCreatedBy().getId().equals(initiator.getId())) {
            throw new IllegalArgumentException("Only the group owner can change member roles.");
        }

        // Cannot change the owner's own role
        if (targetUserId.equals(initiator.getId())) {
            throw new IllegalArgumentException("Cannot change the group owner's role.");
        }

        RoomMember targetMember = roomMemberRepository.findByRoomAndUser_Id(groupChatRoom, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in group."));

        targetMember.setRole(newRole);
        roomMemberRepository.save(targetMember);

        groupChatRoom.setUpdatedAt(LocalDateTime.now());
        groupRepository.save(groupChatRoom);

        // Publish MEMBER_ROLE_CHANGED event
        String roleLabel = newRole == ERoomRole.ADMIN ? "phó nhóm" : "thành viên";
        String sysMsg = displayNameOf(initiator) + " đã thay đổi quyền của " + displayNameOf(targetMember.getUser())
                + " thành " + roleLabel + ".";
        publishGroupEvent(groupChatRoom, ERoomEventType.MEMBER_ROLE_CHANGED, sysMsg, targetMember.getUser());
        publishSystemChatMessage(groupChatRoom, initiator, sysMsg);

        List<RoomMember> members = roomMemberRepository.findAllByRoom(groupChatRoom);
        return buildGroupResponse(groupChatRoom, members);
    }

    @Override
    @Transactional
    public iuh.fit.se.minizalobackend.dtos.response.GroupSettingsResponse getGroupSettings(UUID groupId, User viewer) {
        if (!roomMemberRepository.existsByRoom_IdAndUser_Id(groupId, viewer.getId())) {
            throw new IllegalArgumentException("You are not a member of this group");
        }

        iuh.fit.se.minizalobackend.models.GroupSettings settings = groupSettingsRepository.findByGroupId(groupId)
                .orElse(null);

        // Nếu group chưa có record settings (có thể do dữ liệu cũ), tự tạo mặc định để FE hiển thị được UI.
        if (settings == null) {
            ChatRoom groupChatRoom = groupRepository.findByIdAndType(groupId, ERoomType.GROUP)
                    .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));

            settings = iuh.fit.se.minizalobackend.models.GroupSettings.builder()
                    .group(groupChatRoom)
                    .joinLink(UUID.randomUUID().toString())
                    .build();

            groupSettingsRepository.save(settings);
        }

        return modelMapper.map(settings, iuh.fit.se.minizalobackend.dtos.response.GroupSettingsResponse.class);
    }

    @Override
    @Transactional
    public iuh.fit.se.minizalobackend.dtos.response.GroupSettingsResponse updateGroupSettings(iuh.fit.se.minizalobackend.dtos.request.UpdateGroupSettingsRequest request, User initiator) {
        ChatRoom groupChatRoom = groupRepository.findByIdAndType(request.getGroupId(), ERoomType.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        boolean isOwner = groupChatRoom.getCreatedBy().getId().equals(initiator.getId());
        boolean isAdmin = roomMemberRepository.findByRoomAndUserAndRole(groupChatRoom, initiator, ERoomRole.ADMIN).isPresent();
        if (!isOwner && !isAdmin) {
            throw new IllegalArgumentException("Only admins can update group settings.");
        }

        iuh.fit.se.minizalobackend.models.GroupSettings settings = groupSettingsRepository.findByGroupId(request.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Settings not found for group"));

        if (request.getAllowMemberChangeName() != null) settings.setAllowMemberChangeName(request.getAllowMemberChangeName());
        if (request.getAllowMemberPin() != null) settings.setAllowMemberPin(request.getAllowMemberPin());
        if (request.getAllowMemberCreatePoll() != null) settings.setAllowMemberCreatePoll(request.getAllowMemberCreatePoll());
        if (request.getAllowMemberSendMessage() != null) settings.setAllowMemberSendMessage(request.getAllowMemberSendMessage());
        if (request.getRequireApproval() != null) settings.setRequireApproval(request.getRequireApproval());
        if (request.getAllowNewMemberReadHistory() != null) settings.setAllowNewMemberReadHistory(request.getAllowNewMemberReadHistory());
        if (request.getAllowJoinByLink() != null) settings.setAllowJoinByLink(request.getAllowJoinByLink());

        settings = groupSettingsRepository.save(settings);
        groupChatRoom.setUpdatedAt(LocalDateTime.now());
        groupRepository.save(groupChatRoom);

        // Thông báo SYSTEM trong chat để web/mobile thấy ngay (giống Zalo)
        List<String> changed = new ArrayList<>();
        if (request.getAllowMemberSendMessage() != null) {
            changed.add("quyền gửi tin nhắn " + (request.getAllowMemberSendMessage() ? "được bật" : "đã tắt"));
        }
        if (request.getAllowMemberCreatePoll() != null) {
            changed.add("quyền tạo bình chọn " + (request.getAllowMemberCreatePoll() ? "được bật" : "đã tắt"));
        }
        if (request.getAllowMemberPin() != null) {
            changed.add("quyền ghim tin nhắn " + (request.getAllowMemberPin() ? "được bật" : "đã tắt"));
        }
        if (!changed.isEmpty()) {
            String sysMsg = initiator.getUsername() + " đã cập nhật: " + String.join(", ", changed) + ".";
            publishSystemChatMessage(groupChatRoom, initiator, sysMsg);
        }

        return modelMapper.map(settings, iuh.fit.se.minizalobackend.dtos.response.GroupSettingsResponse.class);
    }

    @Override
    @Transactional
    public GroupResponse transferOwnership(UUID groupId, UUID newOwnerId, User initiator) {
        ChatRoom groupChatRoom = groupRepository.findByIdAndType(groupId, ERoomType.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        if (!groupChatRoom.getCreatedBy().getId().equals(initiator.getId())) {
            throw new IllegalArgumentException("Only current owner can transfer ownership.");
        }

        User newOwner = userRepository.findById(newOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("New owner not found"));

        if (!roomMemberRepository.existsByRoomAndUser(groupChatRoom, newOwner)) {
            throw new IllegalArgumentException("New owner must be a member of the group.");
        }

        groupChatRoom.setCreatedBy(newOwner);
        groupChatRoom.setUpdatedAt(LocalDateTime.now());
        groupRepository.save(groupChatRoom);

        // Ensure new owner is ADMIN
        RoomMember newOwnerMember = roomMemberRepository.findByRoomAndUser(groupChatRoom, newOwner).get();
        newOwnerMember.setRole(ERoomRole.ADMIN);
        roomMemberRepository.save(newOwnerMember);

        String sysMsg = displayNameOf(initiator) + " đã nhường quyền trưởng nhóm cho " + displayNameOf(newOwner) + ".";
        publishGroupEvent(groupChatRoom, ERoomEventType.NAME_CHANGED, sysMsg, newOwner);
        publishSystemChatMessage(groupChatRoom, initiator, sysMsg);

        return buildGroupResponse(groupChatRoom, roomMemberRepository.findAllByRoom(groupChatRoom));
    }

    @Override
    @Transactional
    public void blockMember(UUID groupId, UUID targetUserId, User initiator) {
        ChatRoom groupChatRoom = groupRepository.findByIdAndType(groupId, ERoomType.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        boolean isOwner = groupChatRoom.getCreatedBy().getId().equals(initiator.getId());
        boolean isAdmin = roomMemberRepository.findByRoomAndUserAndRole(groupChatRoom, initiator, ERoomRole.ADMIN).isPresent();

        if (!isOwner && !isAdmin) {
            throw new IllegalArgumentException("Only admins can block members.");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean targetIsOwner = groupChatRoom.getCreatedBy().getId().equals(targetUserId);
        boolean targetIsAdmin = roomMemberRepository.findByRoomAndUserAndRole(groupChatRoom, targetUser, ERoomRole.ADMIN).isPresent();

        if (targetIsOwner || (targetIsAdmin && !isOwner)) {
            throw new IllegalArgumentException("You cannot block this user.");
        }

        if (!blockedGroupMemberRepository.existsByGroupIdAndBlockedUserId(groupId, targetUserId)) {
            iuh.fit.se.minizalobackend.models.BlockedGroupMember blocked = iuh.fit.se.minizalobackend.models.BlockedGroupMember.builder()
                    .group(groupChatRoom)
                    .blockedUser(targetUser)
                    .blockedBy(initiator)
                    .build();
            blockedGroupMemberRepository.save(blocked);
            
            // Remove them if they are in the group
            roomMemberRepository.findByRoomAndUser(groupChatRoom, targetUser).ifPresent(roomMemberRepository::delete);
            
            String sysMsg = displayNameOf(initiator) + " đã chặn " + displayNameOf(targetUser) + " khỏi nhóm.";
            publishGroupEvent(groupChatRoom, ERoomEventType.MEMBER_REMOVED, sysMsg, targetUser);
            publishSystemChatMessage(groupChatRoom, initiator, sysMsg);
        }
    }

    @Override
    @Transactional
    public void unblockMember(UUID groupId, UUID targetUserId, User initiator) {
        ChatRoom groupChatRoom = groupRepository.findByIdAndType(groupId, ERoomType.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        boolean isOwner = groupChatRoom.getCreatedBy().getId().equals(initiator.getId());
        boolean isAdmin = roomMemberRepository.findByRoomAndUserAndRole(groupChatRoom, initiator, ERoomRole.ADMIN).isPresent();

        if (!isOwner && !isAdmin) {
            throw new IllegalArgumentException("Only admins can unblock members.");
        }

        blockedGroupMemberRepository.deleteByGroupIdAndBlockedUserId(groupId, targetUserId);
        try {
            User targetUser = userRepository.findById(targetUserId).orElse(null);
            if (targetUser != null) {
                String sysMsg = displayNameOf(initiator) + " đã bỏ chặn " + displayNameOf(targetUser) + ".";
                publishSystemChatMessage(groupChatRoom, initiator, sysMsg);
            }
        } catch (Exception ignored) { }
    }

    @Override
    @Transactional(readOnly = true)
    public List<iuh.fit.se.minizalobackend.dtos.response.BlockedGroupMemberResponse> getBlockedMembers(UUID groupId, User viewer) {
        if (!roomMemberRepository.findByRoom_IdAndUser_IdAndRole(groupId, viewer.getId(), ERoomRole.ADMIN).isPresent() &&
            !groupRepository.findById(groupId).map(g -> g.getCreatedBy().getId().equals(viewer.getId())).orElse(false)) {
            throw new IllegalArgumentException("Only admins can view blocked members.");
        }

        return blockedGroupMemberRepository.findByGroupId(groupId).stream().map(b -> iuh.fit.se.minizalobackend.dtos.response.BlockedGroupMemberResponse.builder()
                .id(b.getId().toString())
                .userId(b.getBlockedUser().getId().toString())
                .username(b.getBlockedUser().getUsername())
                .displayName(b.getBlockedUser().getDisplayName())
                .avatarUrl(minioService.ensurePublicUrl(b.getBlockedUser().getAvatarUrl()))
                .blockedAt(b.getBlockedAt())
                .blockedBy(b.getBlockedBy().getUsername())
                .build()).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public GroupResponse joinByLink(String joinToken, User user) {
        iuh.fit.se.minizalobackend.models.GroupSettings settings = groupSettingsRepository.findAll().stream()
                .filter(s -> joinToken.equals(s.getJoinLink()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Invalid join link."));

        if (!settings.isAllowJoinByLink()) {
            throw new IllegalArgumentException("Joining by link is not allowed for this group.");
        }

        ChatRoom group = settings.getGroup();
        
        if (blockedGroupMemberRepository.existsByGroupIdAndBlockedUserId(group.getId(), user.getId())) {
            throw new IllegalArgumentException("You are blocked from joining this group.");
        }

        if (roomMemberRepository.existsByRoomAndUser(group, user)) {
            return buildGroupResponse(group, roomMemberRepository.findAllByRoom(group));
        }

        // Add user
        RoomMember member = RoomMember.builder()
                .room(group)
                .user(user)
                .role(ERoomRole.MEMBER)
                .build();
        roomMemberRepository.save(member);

        String sysMsg = user.getUsername() + " đã tham gia nhóm bằng link.";
        publishGroupEvent(group, ERoomEventType.MEMBER_ADDED, sysMsg, user);

        return buildGroupResponse(group, roomMemberRepository.findAllByRoom(group));
    }

    @Override
    @Transactional
    public String refreshJoinLink(UUID groupId, User initiator) {
        ChatRoom groupChatRoom = groupRepository.findByIdAndType(groupId, ERoomType.GROUP)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        boolean isOwner = groupChatRoom.getCreatedBy().getId().equals(initiator.getId());
        boolean isAdmin = roomMemberRepository.findByRoomAndUserAndRole(groupChatRoom, initiator, ERoomRole.ADMIN).isPresent();

        if (!isOwner && !isAdmin) {
            throw new IllegalArgumentException("Only admins can refresh the join link.");
        }

        iuh.fit.se.minizalobackend.models.GroupSettings settings = groupSettingsRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Settings not found"));

        String newLink = UUID.randomUUID().toString();
        settings.setJoinLink(newLink);
        groupSettingsRepository.save(settings);
        
        return newLink;
    }
}