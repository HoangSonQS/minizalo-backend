package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.SocialPostReaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialPostReactionRepository extends JpaRepository<SocialPostReaction, UUID> {
    @EntityGraph(attributePaths = {"post", "user"})
    List<SocialPostReaction> findByPost_IdIn(Collection<UUID> postIds);

    @EntityGraph(attributePaths = {"post", "user"})
    Optional<SocialPostReaction> findByPost_IdAndUser_Id(UUID postId, UUID userId);

    @Modifying
    @Query("delete from SocialPostReaction reaction where reaction.post.id = :postId")
    void deleteByPostId(@Param("postId") UUID postId);
}
