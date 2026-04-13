package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.CallSession;
import iuh.fit.se.minizalobackend.models.ECallStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CallSessionRepository extends JpaRepository<CallSession, UUID> {
    Optional<CallSession> findByIdAndStatus(UUID id, ECallStatus status);
    
    List<CallSession> findByChannelName(String channelName);
    
    List<CallSession> findByCallerIdOrReceiverIdOrderByCreatedAtDesc(UUID callerId, UUID receiverId);

    @Query("""
        SELECT c FROM CallSession c
        WHERE c.receiverId = :receiverId
        AND c.status = 'PENDING'
        AND c.createdAt >= :since
        AND c.delivered = false
        ORDER BY c.createdAt DESC
        LIMIT 1
    """)
    Optional<CallSession> findPendingCallForReceiver(
        @Param("receiverId") UUID receiverId,
        @Param("since") LocalDateTime since
    );
}
