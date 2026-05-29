package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.ModerationFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModerationFlagRepository extends JpaRepository<ModerationFlag, Long> {
    List<ModerationFlag> findAllByStatusOrderByFlaggedAtDesc(String status);
    List<ModerationFlag> findAllByStatusAndTargetTypeOrderByFlaggedAtDesc(String status, String targetType);
    boolean existsByTargetIdAndReporterIdAndStatus(String targetId, String reporterId, String status);
}
