package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.GroupPendingInvitation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupPendingInvitationRepository extends JpaRepository<GroupPendingInvitation, UUID> {

    @EntityGraph(attributePaths = {"candidateUser", "invitedBy"})
    List<GroupPendingInvitation> findByGroup_IdOrderByCreatedAtAsc(UUID groupId);

    boolean existsByGroup_IdAndCandidateUser_Id(UUID groupId, UUID candidateUserId);

    Optional<GroupPendingInvitation> findByGroup_IdAndCandidateUser_Id(UUID groupId, UUID candidateUserId);

    void deleteByGroup_IdAndCandidateUser_Id(UUID groupId, UUID candidateUserId);
}
