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

    @Query("SELECT FUNCTION('DATE', ua.timestamp) as date, COUNT(ua) as count FROM UserActivity ua " +
            "WHERE ua.activityType = 'MESSAGE_SENT' AND ua.timestamp >= :since " +
            "GROUP BY FUNCTION('DATE', ua.timestamp) ORDER BY date")
    List<Object[]> countMessagesPerDay(LocalDateTime since);

    @Query("SELECT FUNCTION('DATE', ua.timestamp) as date, COUNT(DISTINCT ua.user) as count FROM UserActivity ua " +
            "WHERE ua.timestamp >= :since " +
            "GROUP BY FUNCTION('DATE', ua.timestamp) ORDER BY date")
    List<Object[]> countActiveUsersPerDay(LocalDateTime since);
}
