package iuh.fit.se.minizalobackend;

import org.springframework.boot.test.mock.mockito.MockBean;
import io.minio.MinioClient;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
class MiniZaloBackendApplicationTests {

    @MockBean
    private MinioClient minioClient;

    @BeforeEach
    void setUp() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        doNothing().when(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void contextLoads() {
    }

}
