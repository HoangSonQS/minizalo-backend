package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.FriendCategory;
import iuh.fit.se.minizalobackend.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendCategoryRepository extends JpaRepository<FriendCategory, UUID> {
    List<FriendCategory> findByOwnerOrderByCreatedAtAsc(User owner);
    Optional<FriendCategory> findByIdAndOwner(UUID id, User owner);
    boolean existsByIdAndOwner(UUID id, User owner);
}

