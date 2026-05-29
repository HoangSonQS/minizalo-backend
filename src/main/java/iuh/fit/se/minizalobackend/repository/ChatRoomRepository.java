package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import iuh.fit.se.minizalobackend.models.ERoomType;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {
    @Query("SELECT m1.room FROM RoomMember m1 JOIN RoomMember m2 ON m1.room = m2.room WHERE m1.user.id = :user1Id AND m2.user.id = :user2Id AND m1.room.type = :type")
    Optional<ChatRoom> findDirectChatRoom(@Param("user1Id") UUID user1Id, @Param("user2Id") UUID user2Id, @Param("type") ERoomType type);

    /**
     * Find a room of given type that has EXACTLY ONE member (the user).
     * This prevents returning a GROUP/DIRECT room that also contains the user.
     */
    @Query("""
        SELECT m.room
        FROM RoomMember m
        WHERE m.user.id = :userId
          AND m.room.type = :type
          AND (SELECT COUNT(m2) FROM RoomMember m2 WHERE m2.room = m.room) = 1
    """)
    Optional<ChatRoom> findSingleMemberRoom(@Param("userId") UUID userId, @Param("type") ERoomType type);

    /** If DB already has duplicates, return all CLOUD rooms that contain the user. */
    @Query("""
        SELECT m.room
        FROM RoomMember m
        WHERE m.user.id = :userId
          AND m.room.type = :type
    """)
    List<ChatRoom> findRoomsByMemberAndType(@Param("userId") UUID userId, @Param("type") ERoomType type);

    long countByType(ERoomType type);
}
