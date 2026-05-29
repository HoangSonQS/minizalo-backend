package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.ContentReport;
import iuh.fit.se.minizalobackend.models.EReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ContentReportRepository extends JpaRepository<ContentReport, UUID> {
    Page<ContentReport> findByStatusOrderByCreatedAtDesc(EReportStatus status, Pageable pageable);

    Page<ContentReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(EReportStatus status);
}
