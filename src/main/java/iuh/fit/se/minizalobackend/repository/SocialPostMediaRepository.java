package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.SocialPostMedia;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SocialPostMediaRepository extends JpaRepository<SocialPostMedia, UUID> {
    @EntityGraph(attributePaths = "post")
    List<SocialPostMedia> findByPost_IdInOrderBySortOrderAsc(Collection<UUID> postIds);

    @Modifying
    @Query("delete from SocialPostMedia media where media.post.id = :postId")
    void deleteByPostId(@Param("postId") UUID postId);
}
