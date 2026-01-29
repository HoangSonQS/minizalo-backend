package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.ChatRoom;
import iuh.fit.se.minizalobackend.models.ERoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupRepository extends JpaRepository<ChatRoom, UUID> {
    Optional<ChatRoom> findByIdAndType(UUID id, ERoomType type);

    List<ChatRoom> findByCreatedBy_IdAndType(UUID createdById, ERoomType type);
}
