package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.services.MinioService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api")
public class MediaController {

    private final MinioService minioService;

    public MediaController(MinioService minioService) {
        this.minioService = minioService;
    }

    @PostMapping("/media/presigned-url")
    public ResponseEntity<?> getPresignedUrl(@RequestBody PresignedUrlRequest request) {
        try {
            String url = minioService.getPresignedUrl(request.getFolder(), request.getFileName(), request.getContentType());
            Map<String, String> response = new HashMap<>();
            response.put("url", url);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresignedUrlRequest {
        private String folder;
        private String fileName;
        private String contentType;
    }
}
