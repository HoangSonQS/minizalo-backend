package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.RefreshToken;
import iuh.fit.se.minizalobackend.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUser(User user);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM RefreshToken r WHERE r.user = :user")
    void deleteByUser(User user);

    /** Xóa trực tiếp theo user_id (native) để tránh lỗi duplicate key khi tạo token mới. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "DELETE FROM refresh_tokens WHERE user_id = :userId", nativeQuery = true)
    void deleteByUserId(@org.springframework.data.repository.query.Param("userId") UUID userId);
}
