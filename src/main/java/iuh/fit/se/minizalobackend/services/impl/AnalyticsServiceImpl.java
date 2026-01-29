package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.models.UserActivity;
import iuh.fit.se.minizalobackend.repository.UserActivityRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final UserActivityRepository activityRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void logActivity(UUID userId, String activityType, String details) {
        userRepository.findById(userId).ifPresent(user -> {
            UserActivity activity = UserActivity.builder()
                    .user(user)
                    .activityType(activityType)
                    .details(details)
                    .build();
            activityRepository.save(activity);
        });
    }

    @Override
    public Map<String, Object> getUserGrowthStats(LocalDateTime since) {
        Map<String, Object> stats = new HashMap<>();
        long totalUsers = userRepository.count();
        // This is a simplification. Real implementation would aggregate by day.
        stats.put("totalUsers", totalUsers);
        stats.put("since", since.toString());
        return stats;
    }

    @Override
    public Map<String, Object> getMessageVolumeStats(LocalDateTime since) {
        Map<String, Object> stats = new HashMap<>();
        long messageSentCount = activityRepository.countByActivityTypeAndTimestampAfter("MESSAGE_SENT", since);
        stats.put("messagesSent", messageSentCount);
        return stats;
    }

    @Override
    public Map<String, Object> getActiveUserStats(int limit) {
        Map<String, Object> stats = new HashMap<>();
        // Placeholder for active users logic
        stats.put("activeUsersCount", activityRepository.count());
        return stats;
    }
}
