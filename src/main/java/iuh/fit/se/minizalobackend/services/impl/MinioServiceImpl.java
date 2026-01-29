package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.services.MinioService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.UUID;

@Service
public class MinioServiceImpl implements MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucketName}")
    private String bucketName;

    public MinioServiceImpl(MinioClient minioClient) {
        this.minioClient = minioClient;
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
        String objectName = folder + UUID.randomUUID().toString() + "_" + fileName;
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
        return "/" + bucketName + "/" + objectName;
    }

    @Override
    public String getPresignedUrl(String folder, String fileName) {
        String objectName = folder + UUID.randomUUID().toString() + "_" + fileName;
        try {
            return minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.PUT)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(60 * 15) // 15 minutes
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Error generating presigned URL: " + e.getMessage(), e);
        }
    }
}
