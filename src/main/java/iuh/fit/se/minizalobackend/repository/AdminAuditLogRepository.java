package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {
    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
