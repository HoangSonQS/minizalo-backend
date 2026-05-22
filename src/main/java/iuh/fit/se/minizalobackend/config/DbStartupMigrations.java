package iuh.fit.se.minizalobackend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Best-effort DB migrations for local/dev environments.
 *
 * We use Hibernate ddl-auto=update, nhưng nó KHÔNG cập nhật các CHECK constraint đã tồn tại.
 * Vì vậy khi thêm enum value (ví dụ ERoomType.CLOUD) vào DB cũ sẽ bị lỗi 23514.
 */
@Configuration
@Slf4j
public class DbStartupMigrations {

    @Bean
    ApplicationRunner dbStartupMigrationsRunner(JdbcTemplate jdbc) {
        return args -> {
            // 1) Allow new chat room type CLOUD (fix chat_rooms_type_check).
            try {
                jdbc.execute("ALTER TABLE chat_rooms DROP CONSTRAINT IF EXISTS chat_rooms_type_check");
                jdbc.execute(
                        "ALTER TABLE chat_rooms ADD CONSTRAINT chat_rooms_type_check CHECK (type IN ('DIRECT','GROUP','CLOUD'))");
                log.info("[DB-MIGRATION] Updated chat_rooms_type_check to include CLOUD");
            } catch (Exception e) {
                // If table/constraint doesn't exist (fresh DB), ignore.
                log.warn("[DB-MIGRATION] Skip updating chat_rooms_type_check: {}", e.getMessage());
            }

            // 2) Ensure call_sessions.group_call exists and is NOT NULL (fix /api/call/pending 500).
            // Hibernate ddl-auto=update có thể fail khi thêm cột NOT NULL vào bảng đã có dữ liệu.
            try {
                jdbc.execute("ALTER TABLE call_sessions ADD COLUMN IF NOT EXISTS group_call boolean");
                jdbc.execute("UPDATE call_sessions SET group_call = false WHERE group_call IS NULL");
                jdbc.execute("ALTER TABLE call_sessions ALTER COLUMN group_call SET DEFAULT false");
                jdbc.execute("ALTER TABLE call_sessions ALTER COLUMN group_call SET NOT NULL");
                log.info("[DB-MIGRATION] Ensured call_sessions.group_call exists + not null");
            } catch (Exception e) {
                log.warn("[DB-MIGRATION] Skip ensuring call_sessions.group_call: {}", e.getMessage());
            }
        };
    }
}

