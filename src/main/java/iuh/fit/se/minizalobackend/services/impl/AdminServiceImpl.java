package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.models.ChatRoom;
import iuh.fit.se.minizalobackend.models.Role;
import iuh.fit.se.minizalobackend.models.UserActivity;
import iuh.fit.se.minizalobackend.repository.ChatRoomRepository;
import iuh.fit.se.minizalobackend.repository.RoleRepository;
import iuh.fit.se.minizalobackend.repository.UserActivityRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.repository.MessageDynamoRepository;
import iuh.fit.se.minizalobackend.models.RoomMember;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.models.ERoomType;
import iuh.fit.se.minizalobackend.services.AdminService;
import iuh.fit.se.minizalobackend.services.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserActivityRepository userActivityRepository;
    private final RoleRepository roleRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MessageDynamoRepository messageDynamoRepository;
    private final MessageService messageService;

    @Override
    public List<Map<String, Object>> getAllUsers() {
        return userRepository.findAll().stream().map(user -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", user.getId().toString());
            map.put("name", user.getUsername());
            map.put("email", user.getEmail());
            boolean isAdmin = user.getRoles().stream().anyMatch(r -> r.getName().name().equals("ROLE_ADMIN"));
            String role = isAdmin ? "ROLE_ADMIN" : user.getRoles().stream()
                    .map(r -> r.getName().name())
                    .findFirst()
                    .orElse("ROLE_USER");
            map.put("role", role);
            String state = user.getAccountLocked() != null && user.getAccountLocked() ? "Locked" : (user.getIsOnline() != null && user.getIsOnline() ? "Online" : "Active");
            map.put("state", state);
            map.put("messages", 0); // Simplified for MVP
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getAllRooms() {
        return chatRoomRepository.findAll().stream().map(room -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", room.getId().toString());

            List<RoomMember> members = roomMemberRepository.findAllByRoomWithUsersFetched(room);
            map.put("members", members.size());

            String name = room.getName();
            if (name == null || name.isBlank()) {
                if (!members.isEmpty()) {
                    name = members.stream()
                        .map(m -> m.getUser().getDisplayName() != null && !m.getUser().getDisplayName().isBlank() ? m.getUser().getDisplayName() : m.getUser().getUsername())
                        .collect(Collectors.joining(", "));
                } else {
                    name = "Direct Chat";
                }
            }
            map.put("name", name);
            map.put("type", room.getType().name());

            long messagesCount = userActivityRepository.countByActivityTypeAndDetailsContaining(
                "MESSAGE_SENT", room.getId().toString()
            );
            map.put("messages", messagesCount);

            java.util.Optional<UserActivity> lastAct = userActivityRepository.findFirstByActivityTypeAndDetailsContainingOrderByTimestampDesc(
                "MESSAGE_SENT", room.getId().toString()
            );

            if (lastAct.isPresent()) {
                map.put("updatedAt", lastAct.get().getTimestamp().toString());
            } else {
                map.put("updatedAt", room.getUpdatedAt() != null ? room.getUpdatedAt().toString() : "");
            }
            
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public List<iuh.fit.se.minizalobackend.models.MessageDynamo> getMessagesByRoom(String roomId, int limit) {
        return messageDynamoRepository.getMessagesByRoomId(roomId, null, limit).getMessages();
    }

    @Override
    public List<Map<String, Object>> getAuditLogs(int limit) {
        return userActivityRepository.findAll(
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "timestamp"))
        ).stream().map(activity -> {
            Map<String, Object> map = new HashMap<>();
            map.put("time", activity.getTimestamp().toString());
            map.put("actor", activity.getUser() != null ? activity.getUser().getUsername() : "System");
            map.put("action", activity.getActivityType());
            map.put("target", activity.getDetails());
            map.put("status", "Hoàn tất");
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> grantRole(String phone, String roleName) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với SĐT: " + phone));
        
        iuh.fit.se.minizalobackend.models.ERole eRole;
        try {
            eRole = iuh.fit.se.minizalobackend.models.ERole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Quyền không hợp lệ: " + roleName);
        }

        Role role = roleRepository.findByName(eRole)
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName(eRole);
                    return roleRepository.save(newRole);
                });

        if (user.getRoles().contains(role)) {
            throw new RuntimeException("Người dùng đã có quyền này.");
        }

        user.getRoles().add(role);
        userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Đã cấp quyền " + roleName + " cho SĐT " + phone);
        return response;
    }

    @Override
    public void broadcastMessage(String content) {
        List<ChatRoom> rooms = chatRoomRepository.findAll();
        for (ChatRoom room : rooms) {
            if (room.getType() == ERoomType.GROUP || room.getType() == ERoomType.CLOUD) {
                MessageDynamo msg = new MessageDynamo();
                msg.setMessageId(java.util.UUID.randomUUID().toString());
                msg.setChatRoomId(room.getId().toString());
                msg.setSenderId("SYSTEM");
                msg.setSenderName("Hệ thống MiniZalo");
                msg.setContent(content);
                msg.setType("TEXT");
                msg.setCreatedAt(java.time.Instant.now().toString());
                messageService.saveMessage(msg);
            }
        }
    }
}
