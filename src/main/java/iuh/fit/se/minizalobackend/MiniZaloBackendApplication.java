package iuh.fit.se.minizalobackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MiniZaloBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniZaloBackendApplication.class, args);
    }

}
