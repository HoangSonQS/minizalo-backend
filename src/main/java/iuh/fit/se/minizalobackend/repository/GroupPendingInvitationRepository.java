package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.GroupPendingInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupPendingInvitationRepository extends JpaRepository<GroupPendingInvitation, UUID> {
    boolean existsByGroup_IdAndCandidateUser_Id(UUID groupId, UUID candidateUserId);

    Optional<GroupPendingInvitation> findByGroup_IdAndCandidateUser_Id(UUID groupId, UUID candidateUserId);

    List<GroupPendingInvitation> findByGroup_IdOrderByCreatedAtAsc(UUID groupId);
}
