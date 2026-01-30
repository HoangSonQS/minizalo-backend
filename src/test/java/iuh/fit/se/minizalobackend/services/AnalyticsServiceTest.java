package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.repository.UserActivityRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.impl.AnalyticsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AnalyticsServiceTest {

    @Mock
    private UserActivityRepository activityRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AnalyticsServiceImpl analyticsService;

    @Test
    @SuppressWarnings("unchecked")
    public void testGetMessageVolumeStats() {
        // Mock simple count
        when(activityRepository.countByActivityTypeAndTimestampAfter(eq("MESSAGE_SENT"), any())).thenReturn(100L);

        // Mock daily aggregation
        List<Object[]> dailyData = new ArrayList<>();
        dailyData.add(new Object[] { "2023-10-26", 10L });
        dailyData.add(new Object[] { "2023-10-27", 15L });
        when(activityRepository.countMessagesPerDay(any())).thenReturn(dailyData);

        Map<String, Object> stats = analyticsService.getMessageVolumeStats(LocalDateTime.now().minusDays(7));

        assertEquals(100L, stats.get("totalMessages"));
        assertNotNull(stats.get("dailyVolume"));
        List<Map<String, Object>> dailyVolume = (List<Map<String, Object>>) stats.get("dailyVolume");
        assertEquals(2, dailyVolume.size());
        assertEquals("2023-10-26", dailyVolume.get(0).get("date"));
        assertEquals(10L, dailyVolume.get(0).get("count"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetActiveUserStats() {
        // Mock current active users (yesterday)
        List<Object[]> yesterdayUsers = new ArrayList<>();
        yesterdayUsers.add(new Object[] { "2023-10-27", 5L });
        when(activityRepository.countActiveUsersPerDay(any())).thenReturn(yesterdayUsers);

        // Mock daily trend (30 days) - Using same mock for simplicity but logic differs
        // in service args
        // In verify, we could check arguments.

        Map<String, Object> stats = analyticsService.getActiveUserStats(10);

        // Check currentActiveUsers
        assertEquals(1, stats.get("currentActiveUsers")); // Based on yesterdayUsers size

        // Check dailyActiveUsers
        List<Map<String, Object>> dailyTrend = (List<Map<String, Object>>) stats.get("dailyActiveUsers");
        assertEquals(1, dailyTrend.size());
    }
}
