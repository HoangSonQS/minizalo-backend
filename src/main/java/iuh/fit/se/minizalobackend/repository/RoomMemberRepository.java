package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.ChatRoom;
import iuh.fit.se.minizalobackend.models.ERoomRole;
import iuh.fit.se.minizalobackend.models.ERoomType;
import iuh.fit.se.minizalobackend.models.RoomMember;
import iuh.fit.se.minizalobackend.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, UUID> {
    Optional<RoomMember> findByRoomAndUser(ChatRoom room, User user);

    List<RoomMember> findAllByRoom(ChatRoom room);

    List<RoomMember> findByUserId(UUID userId);

    long countByRoomAndRole(ChatRoom room, ERoomRole role);

    Optional<RoomMember> findByRoomAndUser_Id(ChatRoom room, UUID userId);

    List<RoomMember> findByRoomAndUser_IdIn(ChatRoom room, List<UUID> userIds);

    Optional<RoomMember> findByRoomAndUserAndRole(ChatRoom room, User user, ERoomRole role);

    List<RoomMember> findByUserAndRoom_Type(User user, ERoomType roomType);

    boolean existsByRoom_IdAndUser_Id(UUID roomId, UUID userId);
}
