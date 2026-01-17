package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.EFriendStatus;
import iuh.fit.se.minizalobackend.models.Friend;
import iuh.fit.se.minizalobackend.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendRepository extends JpaRepository<Friend, UUID> {
    Optional<Friend> findByUserAndFriend(User user, User friend);
    List<Friend> findByUserAndStatus(User user, EFriendStatus status);
    List<Friend> findByFriendAndStatus(User friend, EFriendStatus status);
}
