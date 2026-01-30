package iuh.fit.se.minizalobackend.config;

import io.minio.MinioClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public MinioClient minioClient() {
        return Mockito.mock(MinioClient.class);
    }
}
