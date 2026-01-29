package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.UserActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserActivityRepository extends JpaRepository<UserActivity, UUID> {

    List<UserActivity> findByUser_IdOrderByTimestampDesc(UUID userId);

    @Query("SELECT COUNT(ua) FROM UserActivity ua WHERE ua.activityType = :type AND ua.timestamp >= :since")
    long countByActivityTypeAndTimestampAfter(String type, LocalDateTime since);
}
