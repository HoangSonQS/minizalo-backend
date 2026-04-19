package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.ChatRoom;
import iuh.fit.se.minizalobackend.models.ERoomRole;
import iuh.fit.se.minizalobackend.models.ERoomType;
import iuh.fit.se.minizalobackend.models.RoomMember;
import iuh.fit.se.minizalobackend.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, UUID> {
    Optional<RoomMember> findByRoomAndUser(ChatRoom room, User user);

    List<RoomMember> findAllByRoom(ChatRoom room);

    /** Eager-load user (tránh LazyInitializationException khi open-in-view=false). */
    @Query("SELECT rm FROM RoomMember rm JOIN FETCH rm.user WHERE rm.room = :room")
    List<RoomMember> findAllByRoomWithUsersFetched(@Param("room") ChatRoom room);

    List<RoomMember> findByUserId(UUID userId);

    long countByRoomAndRole(ChatRoom room, ERoomRole role);

    long countByRoom(ChatRoom room);

    Optional<RoomMember> findByRoomAndUser_Id(ChatRoom room, UUID userId);

    List<RoomMember> findByRoomAndUser_IdIn(ChatRoom room, List<UUID> userIds);

    Optional<RoomMember> findByRoomAndUserAndRole(ChatRoom room, User user, ERoomRole role);

    List<RoomMember> findByUserAndRoom_Type(User user, ERoomType roomType);

    boolean existsByRoom_IdAndUser_Id(UUID roomId, UUID userId);

    boolean existsByRoomAndUser(ChatRoom room, User user);

    Optional<RoomMember> findByRoom_IdAndUser_IdAndRole(UUID roomId, UUID userId, ERoomRole role);
}
