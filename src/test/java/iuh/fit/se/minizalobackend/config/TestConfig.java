package iuh.fit.se.minizalobackend.config;

import io.minio.MinioClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestConfig {

    @Bean(name = "internalMinioClient")
    @Primary
    public MinioClient internalMinioClient() {
        return Mockito.mock(MinioClient.class);
    }

    @Bean(name = "publicMinioClient")
    public MinioClient publicMinioClient() {
        return Mockito.mock(MinioClient.class);
    }
}
