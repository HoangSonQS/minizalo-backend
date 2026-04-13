package iuh.fit.se.minizalobackend.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.publicUrl}")
    private String minioPublicUrl;

    @Value("${minio.accessKey}")
    private String minioAccessKey;

    @Value("${minio.secretKey}")
    private String minioSecretKey;

    @Bean(name = "internalMinioClient")
    public MinioClient generateMinioClient() {
        return MinioClient.builder()
                .endpoint(minioEndpoint)
                .credentials(minioAccessKey, minioSecretKey)
                .build();
    }

    @Bean(name = "publicMinioClient")
    public MinioClient generatePublicMinioClient() {
        return MinioClient.builder()
                .endpoint(minioPublicUrl)
                .credentials(minioAccessKey, minioSecretKey)
                .build();
    }
}
