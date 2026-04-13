package iuh.fit.se.minizalobackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class MiniZaloBackendApplication {
    /**
     * Main method to start the application.
     * 
     * @param args command line arguments
     */

    public static void main(String[] args) {
        SpringApplication.run(MiniZaloBackendApplication.class, args);
    }

}
