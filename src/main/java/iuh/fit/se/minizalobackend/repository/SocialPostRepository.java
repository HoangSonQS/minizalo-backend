package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.SocialPost;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialPostRepository extends JpaRepository<SocialPost, UUID> {
    @EntityGraph(attributePaths = "user")
    List<SocialPost> findByUser_IdInOrderByCreatedAtDesc(Collection<UUID> userIds);

    @Override
    @EntityGraph(attributePaths = "user")
    Optional<SocialPost> findById(UUID id);
}
