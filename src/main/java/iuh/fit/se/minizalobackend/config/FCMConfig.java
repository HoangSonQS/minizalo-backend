package iuh.fit.se.minizalobackend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@Configuration
@Slf4j
public class FCMConfig {

    @Value("${firebase.service-account.path:firebase-service-account.json}")
    private String serviceAccountPath;

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials
                                .fromStream(new ClassPathResource(serviceAccountPath).getInputStream()))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase application has been initialized");
            }
        } catch (IOException e) {
            log.warn("Firebase initialization failed: {}. Push notifications will be disabled.", e.getMessage());
        } catch (Exception e) {
            log.warn(
                    "An unexpected error occurred during Firebase initialization: {}. Push notifications will be disabled.",
                    e.getMessage());
        }
    }
}
