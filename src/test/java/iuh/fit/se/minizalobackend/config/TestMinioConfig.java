package iuh.fit.se.minizalobackend.config;

import iuh.fit.se.minizalobackend.services.MinioService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import static org.mockito.Mockito.mock;

@Profile("test")
@Configuration
public class TestMinioConfig {

    @Bean
    @Primary
    public MinioService minioService() {
        return mock(MinioService.class);
    }
}
