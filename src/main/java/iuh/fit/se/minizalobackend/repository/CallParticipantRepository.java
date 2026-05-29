package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.CallParticipant;
import iuh.fit.se.minizalobackend.models.ECallParticipantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CallParticipantRepository extends JpaRepository<CallParticipant, UUID> {
    List<CallParticipant> findByCallSession_Id(UUID callSessionId);

    Optional<CallParticipant> findByCallSession_IdAndUserId(UUID callSessionId, UUID userId);

    List<CallParticipant> findByCallSession_IdAndStatus(UUID callSessionId, ECallParticipantStatus status);

    @Query("""
        SELECT p FROM CallParticipant p
        JOIN p.callSession s
        WHERE p.userId = :userId
          AND p.status = 'INVITED'
          AND p.delivered = false
          AND s.status = 'PENDING'
          AND s.groupCall = true
        ORDER BY s.createdAt DESC
    """)
    List<CallParticipant> findPendingGroupInvites(@Param("userId") UUID userId);
}

