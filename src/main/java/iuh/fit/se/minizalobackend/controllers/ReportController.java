package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.models.ContentReport;
import iuh.fit.se.minizalobackend.models.EReportStatus;
import iuh.fit.se.minizalobackend.models.EReportTargetType;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.repository.ContentReportRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.security.services.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ContentReportRepository contentReportRepository;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> createReport(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestBody Map<String, String> body) {
        try {
            String targetType = body.get("targetType");
            String targetId = body.get("targetId");
            String reason = body.get("reason");
            if (targetType == null || targetType.isBlank() || targetId == null || targetId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Cần targetType và targetId"));
            }
            if (reason == null || reason.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Cần lý do báo cáo"));
            }

            User reporter = userRepository.findById(userDetails.getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

            EReportTargetType parsedType;
            try {
                parsedType = EReportTargetType.valueOf(targetType.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(Map.of("message", "targetType không hợp lệ"));
            }

            ContentReport report = ContentReport.builder()
                    .reporter(reporter)
                    .targetType(parsedType)
                    .targetId(targetId.trim())
                    .reason(reason.trim())
                    .details(body.get("details"))
                    .status(EReportStatus.PENDING)
                    .build();

            contentReportRepository.save(report);
            return ResponseEntity.ok(Map.of("success", true, "reportId", report.getId().toString()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
