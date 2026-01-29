package iuh.fit.se.minizalobackend.services;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface MinioService {
    String uploadFile(MultipartFile file, String folder, String fileName) throws IOException;
    String getPresignedUrl(String folder, String fileName);
}