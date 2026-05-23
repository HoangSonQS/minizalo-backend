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

            // 3) Ensure social_posts exists for the timeline/story composer.
            // Some dev containers keep the existing DB volume, so Hibernate update may not create
            // this new table before the mobile app calls /api/posts/feed.
            try {
                jdbc.execute("""
                        CREATE TABLE IF NOT EXISTS social_posts (
                            id uuid PRIMARY KEY,
                            user_id uuid NOT NULL,
                            content varchar(4000),
                            media_url varchar(1000),
                            media_type varchar(50),
                            privacy varchar(50),
                            permitted_user_ids varchar(4000),
                            created_at timestamp NOT NULL,
                            CONSTRAINT fk_social_posts_user FOREIGN KEY (user_id) REFERENCES users(id)
                        )
                        """);
                jdbc.execute("ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS privacy varchar(50)");
                jdbc.execute("ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS permitted_user_ids varchar(4000)");
                jdbc.execute("UPDATE social_posts SET privacy = 'ALL_FRIENDS' WHERE privacy IS NULL OR privacy = ''");
                jdbc.execute("""
                        CREATE INDEX IF NOT EXISTS idx_social_posts_user_created_at
                        ON social_posts(user_id, created_at DESC)
                        """);
                log.info("[DB-MIGRATION] Ensured social_posts table exists");
            } catch (Exception e) {
                log.warn("[DB-MIGRATION] Skip ensuring social_posts table: {}", e.getMessage());
            }

            // 4) Ensure post media/comments/reactions exist for timeline interactions.
            try {
                jdbc.execute("""
                        CREATE TABLE IF NOT EXISTS social_post_media (
                            id uuid PRIMARY KEY,
                            post_id uuid NOT NULL,
                            media_url varchar(1000) NOT NULL,
                            media_type varchar(50) NOT NULL,
                            sort_order integer NOT NULL,
                            CONSTRAINT fk_social_post_media_post FOREIGN KEY (post_id) REFERENCES social_posts(id) ON DELETE CASCADE
                        )
                        """);
                jdbc.execute("""
                        CREATE INDEX IF NOT EXISTS idx_social_post_media_post_order
                        ON social_post_media(post_id, sort_order ASC)
                        """);
                jdbc.execute("""
                        CREATE TABLE IF NOT EXISTS social_post_comments (
                            id uuid PRIMARY KEY,
                            post_id uuid NOT NULL,
                            user_id uuid NOT NULL,
                            content varchar(1000) NOT NULL,
                            created_at timestamp NOT NULL,
                            CONSTRAINT fk_social_post_comments_post FOREIGN KEY (post_id) REFERENCES social_posts(id) ON DELETE CASCADE,
                            CONSTRAINT fk_social_post_comments_user FOREIGN KEY (user_id) REFERENCES users(id)
                        )
                        """);
                jdbc.execute("""
                        CREATE INDEX IF NOT EXISTS idx_social_post_comments_post_created_at
                        ON social_post_comments(post_id, created_at ASC)
                        """);
                jdbc.execute("""
                        CREATE TABLE IF NOT EXISTS social_post_reactions (
                            id uuid PRIMARY KEY,
                            post_id uuid NOT NULL,
                            user_id uuid NOT NULL,
                            type varchar(50) NOT NULL,
                            created_at timestamp NOT NULL,
                            CONSTRAINT fk_social_post_reactions_post FOREIGN KEY (post_id) REFERENCES social_posts(id) ON DELETE CASCADE,
                            CONSTRAINT fk_social_post_reactions_user FOREIGN KEY (user_id) REFERENCES users(id),
                            CONSTRAINT uk_social_post_reaction_user UNIQUE (post_id, user_id)
                        )
                        """);
                jdbc.execute("""
                        CREATE INDEX IF NOT EXISTS idx_social_post_reactions_post
                        ON social_post_reactions(post_id)
                        """);
                log.info("[DB-MIGRATION] Ensured social post media/comments/reactions tables exist");
            } catch (Exception e) {
                log.warn("[DB-MIGRATION] Skip ensuring social post interaction tables: {}", e.getMessage());
            }
        };
    }
}

