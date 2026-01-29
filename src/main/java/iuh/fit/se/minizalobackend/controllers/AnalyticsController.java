package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/user-growth")
    public ResponseEntity<Map<String, Object>> getUserGrowth(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        return ResponseEntity.ok(analyticsService.getUserGrowthStats(since));
    }

    @GetMapping("/message-volume")
    public ResponseEntity<Map<String, Object>> getMessageVolume(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        return ResponseEntity.ok(analyticsService.getMessageVolumeStats(since));
    }

    @GetMapping("/active-users")
    public ResponseEntity<Map<String, Object>> getActiveUsers(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getActiveUserStats(limit));
    }
}
