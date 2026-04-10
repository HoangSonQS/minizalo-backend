package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.services.MinioService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.UUID;

@Service
public class MinioServiceImpl implements MinioService {

    private final MinioClient minioClient;
    private final MinioClient publicMinioClient;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Value("${minio.publicUrl}")
    private String minioPublicUrl;

    public MinioServiceImpl(
            @Qualifier("internalMinioClient") MinioClient minioClient, 
            @Qualifier("publicMinioClient") MinioClient publicMinioClient) {
        this.minioClient = minioClient;
        this.publicMinioClient = publicMinioClient;
    }

    @PostConstruct
    public void init() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error initializing MinIO bucket: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadFile(MultipartFile file, String folder, String fileName) throws IOException {
        String sanitizedFileName = fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
        String objectName = folder + UUID.randomUUID().toString() + "_" + sanitizedFileName;
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Error uploading file to MinIO: " + e.getMessage(), e);
        }
        // Return full public URL so the browser can load the file directly
        String base = minioPublicUrl.replaceAll("/$", "");
        return base + "/" + bucketName + "/" + objectName;
    }

    @Override
    public String getPresignedUrl(String folder, String fileName, String contentType) {
        String sanitizedFileName = fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
        String objectName = folder + UUID.randomUUID().toString() + "_" + sanitizedFileName;
        try {
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            headers.put("Content-Type", contentType);

            // Sử dụng publicMinioClient để tạo URL với host public (đúng signature)
            return publicMinioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.PUT)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(60 * 15) // 15 minutes
                            .extraHeaders(headers)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Error generating presigned URL: " + e.getMessage(), e);
        }
    }
}
