package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.services.MinioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable security filters for this unit test
public class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MinioService minioService;

    @Test
    public void testUploadFile_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-image.png",
                MediaType.IMAGE_PNG_VALUE,
                "dummy image content".getBytes());

        String mockUrl = "/minio-bucket/files/unique_test-image.png";
        when(minioService.uploadFile(any(), any(), any())).thenReturn(mockUrl);

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("test-image.png"))
                .andExpect(jsonPath("$.fileUrl").value(mockUrl))
                .andExpect(jsonPath("$.fileType").value(MediaType.IMAGE_PNG_VALUE));
    }

    @Test
    public void testUploadFile_EmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[0]);

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().is4xxClientError()) // Expect 400 Bad Request
                .andExpect(result -> {
                    // Just verifying it failed, or check exception
                    // In real controller it throws IllegalArgumentException
                    // Spring might map this to 500 by default without @ExceptionHandler
                });
    }
}
