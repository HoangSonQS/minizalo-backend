package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.services.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics Controller", description = "APIs for system usage statistics")
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/messages")
    @Operation(summary = "Get message volume statistics")
    // @PreAuthorize("hasRole('ADMIN')") // Uncomment when roles are strictly
    // enforced
    public ResponseEntity<Map<String, Object>> getMessageStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        if (since == null) {
            since = LocalDateTime.now().minusDays(30);
        }
        return ResponseEntity.ok(analyticsService.getMessageVolumeStats(since));
    }

    @GetMapping("/users/active")
    @Operation(summary = "Get active user statistics")
    public ResponseEntity<Map<String, Object>> getActiveUserStats(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getActiveUserStats(limit));
    }

    @GetMapping("/overview")
    @Operation(summary = "Get general system overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        // Reuse existing methods to aggregate an overview
        Map<String, Object> overview = analyticsService.getUserGrowthStats(LocalDateTime.now().minusDays(30));
        return ResponseEntity.ok(overview); // Can be expanded
    }
}
