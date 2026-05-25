package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.SocialPostComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialPostCommentRepository extends JpaRepository<SocialPostComment, UUID> {
    @EntityGraph(attributePaths = {"post", "user"})
    List<SocialPostComment> findByPost_IdInOrderByCreatedAtAsc(Collection<UUID> postIds);

    @EntityGraph(attributePaths = {"post", "user", "post.user"})
    Optional<SocialPostComment> findWithPostAndUserById(UUID id);
}
