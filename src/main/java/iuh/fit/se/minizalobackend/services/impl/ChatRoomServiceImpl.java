package iuh.fit.se.minizalobackend.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.minizalobackend.dtos.request.GroupCreationRequest;
import iuh.fit.se.minizalobackend.dtos.response.ChatRoomResponse;
import iuh.fit.se.minizalobackend.dtos.response.RoomMemberResponse;
import iuh.fit.se.minizalobackend.dtos.response.UserResponse;
import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.exception.custom.*;
import iuh.fit.se.minizalobackend.models.*;
import iuh.fit.se.minizalobackend.repository.ChatRoomRepository;
import iuh.fit.se.minizalobackend.repository.GroupEventRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.services.ChatRoomService;
import iuh.fit.se.minizalobackend.services.UserService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ChatRoomServiceImpl implements ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final GroupEventRepository groupEventRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final iuh.fit.se.minizalobackend.repository.MessageDynamoRepository messageDynamoRepository;

    public ChatRoomServiceImpl(ChatRoomRepository chatRoomRepository,
            RoomMemberRepository roomMemberRepository,
            GroupEventRepository groupEventRepository,
            UserService userService,
            ObjectMapper objectMapper,
            iuh.fit.se.minizalobackend.repository.MessageDynamoRepository messageDynamoRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.groupEventRepository = groupEventRepository;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.messageDynamoRepository = messageDynamoRepository;
    }

    @Override
    @Transactional
    public ChatRoomResponse createGroupChat(GroupCreationRequest request, User createdBy) {
        // 1. Create ChatRoom
        final ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.builder()
                .type(ERoomType.GROUP)
                .name(request.getName())
                .createdBy(createdBy)
                .build());

        // 2. Add creator as ADMIN member
        RoomMember adminMember = RoomMember.builder()
                .room(chatRoom)
                .user(createdBy)
                .role(ERoomRole.ADMIN)
                .build();
        roomMemberRepository.save(adminMember);

        // 3. Add other members
        List<RoomMember> otherMembers = new ArrayList<>();
        List<UUID> distinctMemberIds = request.getMemberIds().stream()
                .filter(memberId -> !memberId.equals(createdBy.getId())) // Don't add creator twice
                .distinct()
                .collect(Collectors.toList());

        for (UUID memberId : distinctMemberIds) {
            Optional<User> memberOptional = userService.getUserById(memberId);
            memberOptional.ifPresent(member -> {
                RoomMember roomMember = RoomMember.builder()
                        .room(chatRoom)
                        .user(member)
                        .role(ERoomRole.MEMBER)
                        .build();
                otherMembers.add(roomMember);
            });
        }
        roomMemberRepository.saveAll(otherMembers);

        // 4. Create GroupEvent for creation
        String metadata = null;
        try {
            metadata = objectMapper.writeValueAsString(Map.of("groupName", chatRoom.getName()));
        } catch (JsonProcessingException e) {
            // Log error, but proceed without metadata
        }

        GroupEvent creationEvent = GroupEvent.builder()
                .group(chatRoom)
                .user(createdBy)
                .eventType(ERoomEventType.CREATED)
                .metadata(metadata)
                .build();
        groupEventRepository.save(creationEvent);

        // 5. Build and return ChatRoomResponse
        List<RoomMemberResponse> memberResponses = new ArrayList<>();
        memberResponses.add(convertToRoomMemberResponse(adminMember));
        otherMembers.forEach(member -> memberResponses.add(convertToRoomMemberResponse(member)));

        return ChatRoomResponse.builder()
                .id(chatRoom.getId())
                .type(chatRoom.getType())
                .name(chatRoom.getName())
                .avatarUrl(chatRoom.getAvatarUrl())
                .createdBy(convertToUserResponse(createdBy))
                .createdAt(chatRoom.getCreatedAt())
                .members(memberResponses)
                .build();
    }

    @Override
    @Transactional
    public ChatRoomResponse addMembersToGroup(UUID groupId, List<UUID> newMemberIds, User actor) {
        ChatRoom chatRoom = chatRoomRepository.findById(groupId)
                .orElseThrow(() -> new ChatRoomNotFoundException("Group chat with ID " + groupId + " not found."));

        // Check if actor is an admin
        RoomMember actorMember = roomMemberRepository.findByRoomAndUser(chatRoom, actor)
                .orElseThrow(() -> new UnauthorizedRoomAccessException("You are not a member of this group."));
        if (actorMember.getRole() != ERoomRole.ADMIN) {
            throw new UnauthorizedRoomAccessException("Only group admins can add members.");
        }

        List<RoomMember> existingMembers = roomMemberRepository.findAllByRoom(chatRoom);
        Set<UUID> existingMemberUserIds = existingMembers.stream()
                .map(member -> member.getUser().getId())
                .collect(Collectors.toSet());

        List<RoomMember> addedMembers = new ArrayList<>();
        List<User> newUsersAdded = new ArrayList<>();

        for (UUID memberId : newMemberIds) {
            if (existingMemberUserIds.contains(memberId)) {
                // Optionally throw UserAlreadyInRoomException or just skip
                continue;
            }

            Optional<User> userOptional = userService.getUserById(memberId);
            userOptional.ifPresent(user -> {
                RoomMember newMember = RoomMember.builder()
                        .room(chatRoom)
                        .user(user)
                        .role(ERoomRole.MEMBER)
                        .build();
                addedMembers.add(newMember);
                newUsersAdded.add(user);
            });
        }

        roomMemberRepository.saveAll(addedMembers);

        if (!addedMembers.isEmpty()) {
            // Create GroupEvent for members added
            String metadata = null;
            try {
                metadata = objectMapper.writeValueAsString(Map.of(
                        "addedBy", actor.getDisplayName(),
                        "addedMembers", newUsersAdded.stream().map(User::getDisplayName).collect(Collectors.toList())));
            } catch (JsonProcessingException e) {
                // Log error
            }
            GroupEvent memberAddedEvent = GroupEvent.builder()
                    .group(chatRoom)
                    .user(actor)
                    .eventType(ERoomEventType.MEMBER_ADDED)
                    .metadata(metadata)
                    .build();
            groupEventRepository.save(memberAddedEvent);
        }

        return getGroupChatDetails(groupId); // Refresh and return updated details
    }

    @Override
    @Transactional
    public ChatRoomResponse removeMemberFromGroup(UUID groupId, UUID memberIdToRemove, User actor) {
        ChatRoom chatRoom = chatRoomRepository.findById(groupId)
                .orElseThrow(() -> new ChatRoomNotFoundException("Group chat with ID " + groupId + " not found."));

        RoomMember actorMember = roomMemberRepository.findByRoomAndUser(chatRoom, actor)
                .orElseThrow(() -> new UnauthorizedRoomAccessException("You are not a member of this group."));
        if (actorMember.getRole() != ERoomRole.ADMIN && !actor.getId().equals(memberIdToRemove)) {
            throw new UnauthorizedRoomAccessException(
                    "Only group admins can remove members or you can leave yourself.");
        }

        if (actorMember.getRole() != ERoomRole.ADMIN && actor.getId().equals(memberIdToRemove)) {
            // If actor is leaving themselves, call leaveGroup
            leaveGroup(groupId, actor);
            return null; // Or a specific response indicating successful leave
        }

        // Only admin can remove other members
        if (actorMember.getRole() != ERoomRole.ADMIN && !actor.getId().equals(memberIdToRemove)) {
            throw new UnauthorizedRoomAccessException("Only group admins can remove other members.");
        }

        RoomMember memberToRemove = roomMemberRepository.findByRoomAndUser_Id(chatRoom, memberIdToRemove)
                .orElseThrow(() -> new UserNotInRoomException(
                        "User with ID " + memberIdToRemove + " is not a member of this group."));

        if (memberToRemove.getRole() == ERoomRole.ADMIN && !actor.getId().equals(memberToRemove.getUser().getId())) {
            long adminCount = roomMemberRepository.countByRoomAndRole(chatRoom, ERoomRole.ADMIN);
            if (adminCount == 1) {
                throw new CannotRemoveAdminException(
                        "Cannot remove the last admin from the group. Transfer admin role first.");
            }
        }

        roomMemberRepository.delete(memberToRemove);

        // Create GroupEvent for member removed
        String metadata = null;
        try {
            metadata = objectMapper.writeValueAsString(Map.of(
                    "removedBy", actor.getDisplayName(),
                    "removedMember", memberToRemove.getUser().getDisplayName()));
        } catch (JsonProcessingException e) {
            // Log error
        }
        GroupEvent memberRemovedEvent = GroupEvent.builder()
                .group(chatRoom)
                .user(actor)
                .eventType(ERoomEventType.MEMBER_REMOVED)
                .metadata(metadata)
                .build();
        groupEventRepository.save(memberRemovedEvent);

        return getGroupChatDetails(groupId);
    }

    @Override
    @Transactional
    public ChatRoomResponse updateGroupInfo(UUID groupId, String newName, String newAvatarUrl, User actor) {
        ChatRoom chatRoom = chatRoomRepository.findById(groupId)
                .orElseThrow(() -> new ChatRoomNotFoundException("Group chat with ID " + groupId + " not found."));

        RoomMember actorMember = roomMemberRepository.findByRoomAndUser(chatRoom, actor)
                .orElseThrow(() -> new UnauthorizedRoomAccessException("You are not a member of this group."));
        if (actorMember.getRole() != ERoomRole.ADMIN) {
            throw new UnauthorizedRoomAccessException("Only group admins can update group information.");
        }

        String metadata = null;
        Map<String, String> changes = new HashMap<>();

        if (newName != null && !newName.isBlank() && !newName.equals(chatRoom.getName())) {
            changes.put("oldName", chatRoom.getName());
            changes.put("newName", newName);
            chatRoom.setName(newName);
        }
        if (newAvatarUrl != null && !newAvatarUrl.isBlank() && !newAvatarUrl.equals(chatRoom.getAvatarUrl())) {
            changes.put("oldAvatarUrl", chatRoom.getAvatarUrl());
            changes.put("newAvatarUrl", newAvatarUrl);
            chatRoom.setAvatarUrl(newAvatarUrl);
        }

        if (!changes.isEmpty()) {
            chatRoomRepository.save(chatRoom);
            try {
                metadata = objectMapper.writeValueAsString(changes);
            } catch (JsonProcessingException e) {
                // Log error
            }

            ERoomEventType eventType = ERoomEventType.NAME_CHANGED; // Default
            if (changes.containsKey("newAvatarUrl") && !changes.containsKey("newName")) {
                eventType = ERoomEventType.AVATAR_CHANGED;
            } else if (changes.containsKey("newName") && !changes.containsKey("newAvatarUrl")) {
                eventType = ERoomEventType.NAME_CHANGED;
            } else if (changes.containsKey("newName") && changes.containsKey("newAvatarUrl")) {
                eventType = ERoomEventType.NAME_CHANGED; // Or a combined event type
            }

            GroupEvent updateEvent = GroupEvent.builder()
                    .group(chatRoom)
                    .user(actor)
                    .eventType(eventType)
                    .metadata(metadata)
                    .build();
            groupEventRepository.save(updateEvent);
        }

        return getGroupChatDetails(groupId);
    }

    @Override
    @Transactional
    public void leaveGroup(UUID groupId, User actor) {
        ChatRoom chatRoom = chatRoomRepository.findById(groupId)
                .orElseThrow(() -> new ChatRoomNotFoundException("Group chat with ID " + groupId + " not found."));

        RoomMember memberToLeave = roomMemberRepository.findByRoomAndUser(chatRoom, actor)
                .orElseThrow(() -> new UserNotInRoomException(
                        "User " + actor.getDisplayName() + " is not a member of this group."));

        if (memberToLeave.getRole() == ERoomRole.ADMIN) {
            long adminCount = roomMemberRepository.countByRoomAndRole(chatRoom, ERoomRole.ADMIN);
            if (adminCount == 1) {
                throw new CannotRemoveAdminException(
                        "The last admin cannot leave the group. Transfer admin role first.");
            }
        }

        roomMemberRepository.delete(memberToLeave);

        // Create GroupEvent for user leaving
        String metadata = null;
        try {
            metadata = objectMapper.writeValueAsString(Map.of("leftMember", actor.getDisplayName()));
        } catch (JsonProcessingException e) {
            // Log error
        }
        GroupEvent leaveEvent = GroupEvent.builder()
                .group(chatRoom)
                .user(actor)
                .eventType(ERoomEventType.ROOM_LEFT)
                .metadata(metadata)
                .build();
        groupEventRepository.save(leaveEvent);
    }

    @Override
    public ChatRoomResponse getGroupChatDetails(UUID groupId) {
        ChatRoom chatRoom = chatRoomRepository.findById(groupId)
                .orElseThrow(() -> new ChatRoomNotFoundException("Group chat with ID " + groupId + " not found."));

        List<RoomMember> members = roomMemberRepository.findAllByRoom(chatRoom);
        List<RoomMemberResponse> memberResponses = members.stream()
                .map(this::convertToRoomMemberResponse)
                .collect(Collectors.toList());

        return ChatRoomResponse.builder()
                .id(chatRoom.getId())
                .type(chatRoom.getType())
                .name(chatRoom.getName())
                .avatarUrl(chatRoom.getAvatarUrl())
                .createdBy(convertToUserResponse(chatRoom.getCreatedBy()))
                .createdAt(chatRoom.getCreatedAt())
                .members(memberResponses)
                .build();
    }

    @Override
    @Transactional
    public ChatRoomResponse changeMemberRole(UUID groupId, UUID memberId, ERoomRole newRole, User actor) {
        ChatRoom chatRoom = chatRoomRepository.findById(groupId)
                .orElseThrow(() -> new ChatRoomNotFoundException("Group chat with ID " + groupId + " not found."));

        RoomMember actorMember = roomMemberRepository.findByRoomAndUser(chatRoom, actor)
                .orElseThrow(() -> new UnauthorizedRoomAccessException("You are not a member of this group."));
        if (actorMember.getRole() != ERoomRole.ADMIN) {
            throw new UnauthorizedRoomAccessException("Only group admins can change member roles.");
        }

        RoomMember targetMember = roomMemberRepository.findByRoomAndUser_Id(chatRoom, memberId)
                .orElseThrow(() -> new UserNotInRoomException(
                        "User with ID " + memberId + " is not a member of this group."));

        if (targetMember.getRole() == newRole) {
            return getGroupChatDetails(groupId); // No change needed
        }

        // Prevent admin from demoting themselves if they are the last admin
        if (targetMember.getRole() == ERoomRole.ADMIN && newRole != ERoomRole.ADMIN) {
            long adminCount = roomMemberRepository.countByRoomAndRole(chatRoom, ERoomRole.ADMIN);
            if (adminCount == 1) {
                throw new CannotRemoveAdminException("Cannot demote the last admin of the group.");
            }
        }

        ERoomRole oldRole = targetMember.getRole();
        targetMember.setRole(newRole);
        roomMemberRepository.save(targetMember);

        // Create GroupEvent for role change
        String metadata = null;
        try {
            metadata = objectMapper.writeValueAsString(Map.of(
                    "changedBy", actor.getDisplayName(),
                    "member", targetMember.getUser().getDisplayName(),
                    "oldRole", oldRole.name(),
                    "newRole", newRole.name()));
        } catch (JsonProcessingException e) {
            // Log error
        }
        GroupEvent roleChangeEvent = GroupEvent.builder()
                .group(chatRoom)
                .user(actor)
                .eventType(ERoomEventType.MEMBER_ROLE_CHANGED)
                .metadata(metadata)
                .build();
        groupEventRepository.save(roleChangeEvent);

        return getGroupChatDetails(groupId);
    }



    @Override
    @Transactional
    public ChatRoomResponse createDirectChat(User user1, User user2) {
        log.info("Creating/checking direct chat between {} and {}", user1.getUsername(), user2.getUsername());
        Optional<ChatRoom> existingChat = chatRoomRepository.findDirectChatRoom(user1.getId(), user2.getId(), ERoomType.DIRECT);
        if (existingChat.isPresent()) {
            log.info("Found existing direct chat: {}", existingChat.get().getId());
            return getGroupChatDetails(existingChat.get().getId());
        }

        log.info("Creating new direct chat");
        ChatRoom chatRoom = ChatRoom.builder()
                .type(ERoomType.DIRECT)
                .createdBy(user1)
                .build();
        chatRoom = chatRoomRepository.save(chatRoom);

        RoomMember member1 = RoomMember.builder()
                .room(chatRoom)
                .user(user1)
                .role(ERoomRole.MEMBER)
                .build();
        RoomMember member2 = RoomMember.builder()
                .room(chatRoom)
                .user(user2)
                .role(ERoomRole.MEMBER)
                .build();
        roomMemberRepository.saveAll(List.of(member1, member2));
        log.info("New direct chat created: {}", chatRoom.getId());

        return getGroupChatDetails(chatRoom.getId());
    }

    @Override
    @Transactional
    public List<ChatRoomResponse> getChatRoomsForUser(User user) {
        log.info("Fetching chat rooms for user ID: {}", user.getId());
        List<RoomMember> memberships = roomMemberRepository.findByUserId(user.getId());
        log.info("Found {} memberships for user {}", memberships.size(), user.getUsername());
        List<ChatRoomResponse> responses = new ArrayList<>();

        for (RoomMember membership : memberships) {
            try {
                ChatRoom room = membership.getRoom();
                if (room == null) {
                    log.warn("Room is null for membership {}", membership.getId());
                    continue;
                }
                
                log.info("Getting details for room {}", room.getId());
                ChatRoomResponse response = getGroupChatDetails(room.getId());
                log.info("Got details for room {}", room.getId());

                if (room.getType() == ERoomType.DIRECT) {
                    response.getMembers().stream()
                        .filter(m -> !m.getUser().getId().equals(user.getId()))
                        .findFirst()
                        .ifPresent(otherMember -> {
                            response.setName(otherMember.getUser().getDisplayName());
                            response.setAvatarUrl(otherMember.getUser().getAvatarUrl());
                        });
                }

                // Fetch last message from DynamoDB
                try {
                    PaginatedMessageResult lastMsgResult = messageDynamoRepository.getMessagesByRoomId(
                            room.getId().toString(), null, 1);
                    if (lastMsgResult != null && lastMsgResult.getMessages() != null 
                            && !lastMsgResult.getMessages().isEmpty()) {
                        response.setLastMessage(lastMsgResult.getMessages().get(0));
                    }
                } catch (Exception ex) {
                    log.warn("Could not fetch last message for room {}: {}", room.getId(), ex.getMessage());
                }

                responses.add(response);
            } catch (Exception e) {
                log.error("Error processing membership {}: {}", membership.getId(), e.getMessage());
            }
        }

        log.info("Sorting {} responses", responses.size());
        responses.sort((r1, r2) -> {
             String t1 = r1.getLastMessage() != null ? r1.getLastMessage().getCreatedAt() : (r1.getCreatedAt() != null ? r1.getCreatedAt().toString() : "");
             String t2 = r2.getLastMessage() != null ? r2.getLastMessage().getCreatedAt() : (r2.getCreatedAt() != null ? r2.getCreatedAt().toString() : "");
             return t2.compareTo(t1);
        });

        log.info("Returning {} chat rooms for user", responses.size());
        return responses;
    }

    private UserResponse convertToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    private RoomMemberResponse convertToRoomMemberResponse(RoomMember roomMember) {
        return RoomMemberResponse.builder()
                .id(roomMember.getId())
                .user(convertToUserResponse(roomMember.getUser()))
                .role(roomMember.getRole())
                .joinedAt(roomMember.getJoinedAt())
                .lastReadAt(roomMember.getLastReadAt())
                .build();
    }
}
