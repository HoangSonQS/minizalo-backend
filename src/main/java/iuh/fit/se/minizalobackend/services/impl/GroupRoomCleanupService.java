package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.models.ChatRoom;
import iuh.fit.se.minizalobackend.repository.BlockedGroupMemberRepository;
import iuh.fit.se.minizalobackend.repository.GroupEventRepository;
import iuh.fit.se.minizalobackend.repository.GroupRepository;
import iuh.fit.se.minizalobackend.repository.GroupSettingsRepository;
import iuh.fit.se.minizalobackend.repository.PollRepository;
import iuh.fit.se.minizalobackend.repository.RoomMemberRepository;
import iuh.fit.se.minizalobackend.services.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Xóa nhóm và toàn bộ dữ liệu phụ thuộc (FK-safe).
 */
@Service
@RequiredArgsConstructor
public class GroupRoomCleanupService {

    private final MessageService messageService;
    private final BlockedGroupMemberRepository blockedGroupMemberRepository;
    private final GroupSettingsRepository groupSettingsRepository;
    private final PollRepository pollRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final GroupEventRepository groupEventRepository;
    private final GroupRepository groupRepository;

    @Transactional
    public void hardDeleteGroup(ChatRoom group) {
        UUID groupId = group.getId();
        messageService.deleteAllMessages(groupId.toString());
        blockedGroupMemberRepository.deleteAll(blockedGroupMemberRepository.findByGroup(group));
        groupSettingsRepository.findByGroupId(groupId).ifPresent(groupSettingsRepository::delete);
        pollRepository.deleteAll(pollRepository.findByRoomIdOrderByCreatedAtDesc(groupId));
        roomMemberRepository.deleteAll(roomMemberRepository.findAllByRoom(group));
        groupEventRepository.deleteAllByGroup(group);
        groupRepository.delete(group);
    }
}
