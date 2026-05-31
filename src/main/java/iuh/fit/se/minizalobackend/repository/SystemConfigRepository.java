package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {
}
