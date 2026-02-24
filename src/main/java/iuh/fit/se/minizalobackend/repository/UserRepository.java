package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

    // Tìm kiếm danh sách user theo username (case-insensitive)
    List<User> findByUsernameContainingIgnoreCase(String username);

    // Tìm kiếm user theo phone (chính xác) và danh sách theo phone (nếu cần)
    Optional<User> findByPhone(String phone);
    List<User> findByPhoneContainingIgnoreCase(String phone);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE User u SET u.isOnline = false")
    void updateAllUsersOffline();
}
