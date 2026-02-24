package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.FriendCategoryAssignment;
import iuh.fit.se.minizalobackend.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendCategoryAssignmentRepository extends JpaRepository<FriendCategoryAssignment, UUID> {
    Optional<FriendCategoryAssignment> findByOwnerAndTarget(User owner, User target);
    List<FriendCategoryAssignment> findByOwner(User owner);
}

