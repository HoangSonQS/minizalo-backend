package iuh.fit.se.minizalobackend.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Entity
@Table(name = "system_configs")
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfig {

    @Id
    @Column(name = "config_key", unique = true, nullable = false)
    private String key;

    @Column(name = "config_value", columnDefinition = "TEXT", nullable = false)
    private String value;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Instant updatedAt = Instant.now();

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
