package iuh.fit.se.minizalobackend.config;

import iuh.fit.se.minizalobackend.models.ERole;
import iuh.fit.se.minizalobackend.models.Role;
import iuh.fit.se.minizalobackend.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration to initialize roles for integration tests.
 * This ensures roles exist in the database before tests run,
 * solving the issue where CommandLineRunner doesn't execute in test context.
 */
@TestConfiguration
@Profile("test")
@Slf4j
@RequiredArgsConstructor
public class TestDataConfig {

    private final RoleRepository roleRepository;

    @Bean
    public RoleInitializer roleInitializer() {
        return new RoleInitializer(roleRepository);
    }

    public static class RoleInitializer {
        private final RoleRepository roleRepository;

        public RoleInitializer(RoleRepository roleRepository) {
            this.roleRepository = roleRepository;
            initializeRoles();
        }

        private void initializeRoles() {
            log.info("Initializing roles for test environment...");

            if (roleRepository.findByName(ERole.ROLE_USER).isEmpty()) {
                roleRepository.save(new Role(null, ERole.ROLE_USER));
                log.debug("Created ROLE_USER");
            }

            if (roleRepository.findByName(ERole.ROLE_MODERATOR).isEmpty()) {
                roleRepository.save(new Role(null, ERole.ROLE_MODERATOR));
                log.debug("Created ROLE_MODERATOR");
            }

            if (roleRepository.findByName(ERole.ROLE_ADMIN).isEmpty()) {
                roleRepository.save(new Role(null, ERole.ROLE_ADMIN));
                log.debug("Created ROLE_ADMIN");
            }

            log.info("Roles initialized successfully for test environment");
        }
    }
}
