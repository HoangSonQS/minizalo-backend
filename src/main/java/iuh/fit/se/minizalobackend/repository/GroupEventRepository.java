package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.GroupEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GroupEventRepository extends JpaRepository<GroupEvent, UUID> {
    List<GroupEvent> findByGroupIdOrderByCreatedAtDesc(UUID groupId);
}
