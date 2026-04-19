package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PollOptionRepository extends JpaRepository<PollOption, UUID> {
}
