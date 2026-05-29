package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.models.UserActivity;
import iuh.fit.se.minizalobackend.repository.UserActivityRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.AnalyticsService;
import iuh.fit.se.minizalobackend.utils.AppConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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
        long totalMessages = activityRepository.countByActivityTypeAndTimestampAfter(AppConstants.ACTIVITY_MESSAGE_SENT,
                since);
        stats.put("totalMessages", totalMessages);

        // Time series data
        stats.put("dailyVolume", activityRepository.countMessagesPerDay(since).stream()
                .map(row -> Map.of("date", row[0].toString(), "count", row[1]))
                .toList());
        return stats;
    }

    @Override
    public Map<String, Object> getActiveUserStats(int limit) {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime since = LocalDateTime.now().minusDays(30); // Default lookback 30 days

        List<Object[]> dailyActiveData = activityRepository.countActiveUsersPerDay(since);
        
        long currentActive = 0;
        if (!dailyActiveData.isEmpty()) {
            currentActive = ((Number) dailyActiveData.get(dailyActiveData.size() - 1)[1]).longValue();
        }
        
        stats.put("currentActiveUsers", currentActive);

        // Time series
        stats.put("dailyActiveUsers", dailyActiveData.stream()
                .map(row -> Map.of("date", row[0].toString(), "count", row[1]))
                .toList());
        return stats;
    }
}
