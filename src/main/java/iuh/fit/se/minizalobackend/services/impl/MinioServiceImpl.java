package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.services.MinioService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.UUID;

@Service
@Profile("!test")
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
        // Trả về đường dẫn tương đối thay vì tuyệt đối để linh hoạt khi đổi IP
        return bucketName + "/" + objectName;
    }

    @Override
    public String ensurePublicUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        
        // Nếu đã là URL tuyệt đối (bắt đầu bằng http)
        if (url.startsWith("http")) {
            // Nếu URL chứa tên bucket, ta bóc tách phần đuôi sau bucket để gắn IP mới
            // Ví dụ: http://old-ip:9000/minizalo-bucket/avatars/xxx.jpg -> avatars/xxx.jpg
            String searchStr = "/" + bucketName + "/";
            int index = url.indexOf(searchStr);
            if (index != -1) {
                String relativePath = url.substring(index + searchStr.length());
                String base = minioPublicUrl.replaceAll("/$", "");
                return base + "/" + bucketName + "/" + relativePath;
            }
            return url; // Không nhận diện được cấu trúc, giữ nguyên hoặc log cảnh báo
        }
        
        // Nếu là đường dẫn tương đối (ví dụ: minizalo-bucket/avatars/xxx.jpg hoặc avatars/xxx.jpg)
        String base = minioPublicUrl.replaceAll("/$", "");
        if (url.startsWith(bucketName + "/")) {
            return base + "/" + url;
        }
        return base + "/" + bucketName + "/" + url;
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
