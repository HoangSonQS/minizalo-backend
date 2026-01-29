package iuh.fit.se.minizalobackend.services;

import java.time.LocalDateTime;
import java.util.Map;

public interface AnalyticsService {
    void logActivity(java.util.UUID userId, String activityType, String details);

    Map<String, Object> getUserGrowthStats(LocalDateTime since);

    Map<String, Object> getMessageVolumeStats(LocalDateTime since);

    Map<String, Object> getActiveUserStats(int limit);
}
