package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    // Tìm theo username (đang dùng cho đăng nhập, ở đây vẫn giữ Optional)
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Boolean existsByUsername(String username);

    Boolean existsByEmail(String email);

    Boolean existsByEmailIgnoreCase(String email);

    Boolean existsByPhone(String phone);

    // Tìm kiếm danh sách user theo username (case-insensitive)
    List<User> findByUsernameContainingIgnoreCase(String username);

    @Query("SELECT u FROM User u WHERE LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<User> searchByDisplayNameOrUsername(@Param("q") String q);

    // Tìm kiếm user theo phone (chính xác) và danh sách theo phone (nếu cần)
    Optional<User> findByPhone(String phone);

    List<User> findByPhoneContainingIgnoreCase(String phone);

    List<User> findByPhoneIn(List<String> phones);

    @Modifying
    @Query("UPDATE User u SET u.isOnline = false")
    @Transactional
    void updateAllUsersOffline();

    long countByAccountLockedTrue();

    @Query("""
            SELECT u FROM User u
            WHERE (:q IS NULL OR :q = '' OR
                   LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%')) OR
                   LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) OR
                   LOWER(u.phone) LIKE LOWER(CONCAT('%', :q, '%')) OR
                   LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:locked IS NULL OR u.accountLocked = :locked)
            """)
    Page<User> adminSearchUsers(@Param("q") String q, @Param("locked") Boolean locked, Pageable pageable);

    @Query("""
            SELECT DISTINCT u FROM User u JOIN u.roles r
            WHERE r.name = iuh.fit.se.minizalobackend.models.ERole.ROLE_ADMIN
            """)
    List<User> findAllAdmins();
}
