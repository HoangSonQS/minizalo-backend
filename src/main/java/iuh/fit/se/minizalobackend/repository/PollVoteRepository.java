package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PollVoteRepository extends JpaRepository<PollVote, UUID> {
    Optional<PollVote> findByOptionIdAndUserId(UUID optionId, UUID userId);

    @Modifying
    @Query("DELETE FROM PollVote pv WHERE pv.option.poll.id = :pollId AND pv.user.id = :userId")
    void deleteByPollIdAndUserId(@Param("pollId") UUID pollId, @Param("userId") UUID userId);
}
