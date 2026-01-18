package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.services.MinioService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MediaController {

    private final MinioService minioService;

    public MediaController(MinioService minioService) {
        this.minioService = minioService;
    }

    @PostMapping("/media/presigned-url")
    public String getPresignedUrl(@RequestBody PresignedUrlRequest request) {
        return minioService.getPresignedUrl(request.getFolder(), request.getFileName());
    }

    private static class PresignedUrlRequest {
        private String folder;
        private String fileName;

        public String getFolder() {
            return folder;
        }

        public String getFileName() {
            return fileName;
        }
    }
}
