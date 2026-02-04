package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.dtos.response.FileUploadResponse;
import iuh.fit.se.minizalobackend.services.MinioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Controller", description = "APIs for file upload and management")
@SecurityRequirement(name = "bearerAuth")
public class FileController {

    private final MinioService minioService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file (Image, Video, Document)")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        long size = file.getSize();

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        String fileUrl = minioService.uploadFile(file, "files/", fileName);

        FileUploadResponse response = FileUploadResponse.builder()
                .fileName(fileName)
                .fileUrl(fileUrl)
                .fileType(contentType)
                .size(size)
                .build();

        return ResponseEntity.ok(response);
    }
}
