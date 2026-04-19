package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.GroupSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupSettingsRepository extends JpaRepository<GroupSettings, UUID> {
    Optional<GroupSettings> findByGroupId(UUID groupId);
}
