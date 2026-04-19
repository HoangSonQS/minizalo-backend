package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.BlockedGroupMember;
import iuh.fit.se.minizalobackend.models.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlockedGroupMemberRepository extends JpaRepository<BlockedGroupMember, UUID> {
    List<BlockedGroupMember> findByGroupId(UUID groupId);
    List<BlockedGroupMember> findByGroup(ChatRoom group);
    boolean existsByGroupIdAndBlockedUserId(UUID groupId, UUID userId);
    Optional<BlockedGroupMember> findByGroupIdAndBlockedUserId(UUID groupId, UUID userId);
    void deleteByGroupIdAndBlockedUserId(UUID groupId, UUID userId);
}
