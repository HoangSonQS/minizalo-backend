package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import iuh.fit.se.minizalobackend.models.ERoomType;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {
    @Query("SELECT m1.room FROM RoomMember m1 JOIN RoomMember m2 ON m1.room = m2.room WHERE m1.user.id = :user1Id AND m2.user.id = :user2Id AND m1.room.type = :type")
    Optional<ChatRoom> findDirectChatRoom(@Param("user1Id") UUID user1Id, @Param("user2Id") UUID user2Id, @Param("type") ERoomType type);
}
