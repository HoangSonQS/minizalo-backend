package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.models.EFriendStatus;
import iuh.fit.se.minizalobackend.models.Friend;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.payload.response.FriendResponse;
import iuh.fit.se.minizalobackend.payload.response.UserResponse;
import iuh.fit.se.minizalobackend.repository.FriendRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserService userService;

    public FriendService(FriendRepository friendRepository, UserService userService) {
        this.friendRepository = friendRepository;
        this.userService = userService;
    }

    @Transactional
    public FriendResponse sendFriendRequest(UUID senderId, UUID receiverId) {
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("Cannot send friend request to self.");
        }

        User sender = userService.getUserById(senderId);
        User receiver = userService.getUserById(receiverId);

        // Check if a request already exists or they are already friends
        Optional<Friend> existingFriendship = friendRepository.findByUserAndFriend(sender, receiver);
        if (existingFriendship.isPresent()) {
            throw new IllegalStateException("Friend request already sent or they are already friends.");
        }

        Optional<Friend> existingReverseFriendship = friendRepository.findByUserAndFriend(receiver, sender);
        if (existingReverseFriendship.isPresent()
                && existingReverseFriendship.get().getStatus() == EFriendStatus.PENDING) {
            throw new IllegalStateException("You have a pending friend request from this user. Accept it instead.");
        }

        Friend friendRequest = new Friend(null, sender, receiver, EFriendStatus.PENDING, null);
        return mapFriendToFriendResponse(friendRepository.save(friendRequest));
    }

    @Transactional
    public FriendResponse acceptFriendRequest(UUID currentUserId, UUID requestId) {
        Friend friendRequest = friendRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found."));

        if (!friendRequest.getFriend().getId().equals(currentUserId)) {
            throw new SecurityException("You are not authorized to accept this request.");
        }
        if (friendRequest.getStatus() != EFriendStatus.PENDING) {
            throw new IllegalStateException("Friend request is not pending.");
        }

        friendRequest.setStatus(EFriendStatus.ACCEPTED);
        Friend acceptedRequest = friendRepository.save(friendRequest);

        // Create a reciprocal friendship for the sender
        Friend reciprocalFriendship = new Friend(null, friendRequest.getFriend(), friendRequest.getUser(),
                EFriendStatus.ACCEPTED, null);
        friendRepository.save(reciprocalFriendship);

        return mapFriendToFriendResponse(acceptedRequest);
    }

    @Transactional
    public void rejectFriendRequest(UUID currentUserId, UUID requestId) {
        Friend friendRequest = friendRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found."));

        if (!friendRequest.getFriend().getId().equals(currentUserId)) {
            throw new SecurityException("You are not authorized to reject this request.");
        }
        if (friendRequest.getStatus() != EFriendStatus.PENDING) {
            throw new IllegalStateException("Friend request is not pending.");
        }

        friendRepository.delete(friendRequest);
    }

    @Transactional
    public void deleteFriend(UUID currentUserId, UUID friendIdToDelete) {
        User currentUser = userService.getUserById(currentUserId);
        User friendUser = userService.getUserById(friendIdToDelete);

        // Delete friendship from current user to friend
        Optional<Friend> friendship1 = friendRepository.findByUserAndFriend(currentUser, friendUser);
        friendship1.ifPresent(friendRepository::delete);

        // Delete reciprocal friendship from friend to current user
        Optional<Friend> friendship2 = friendRepository.findByUserAndFriend(friendUser, currentUser);
        friendship2.ifPresent(friendRepository::delete);
    }

    public List<FriendResponse> getFriendsList(UUID userId) {
        User currentUser = userService.getUserById(userId);
        return friendRepository.findByUserAndStatus(currentUser, EFriendStatus.ACCEPTED).stream()
                .map(this::mapFriendToFriendResponse)
                .collect(Collectors.toList());
    }

    public List<FriendResponse> getPendingFriendRequests(UUID userId) {
        User currentUser = userService.getUserById(userId);
        // Find requests where current user is the friend (receiver) and status is
        // PENDING
        return friendRepository.findByFriendAndStatus(currentUser, EFriendStatus.PENDING).stream()
                .map(this::mapFriendToFriendResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void blockUser(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("Cannot block self.");
        }
        User blocker = userService.getUserById(blockerId);
        User blocked = userService.getUserById(blockedId);

        // Remove any existing friendship/request between them
        friendRepository.findByUserAndFriend(blocker, blocked).ifPresent(friendRepository::delete);
        friendRepository.findByUserAndFriend(blocked, blocker).ifPresent(friendRepository::delete);

        // Create a new block entry
        Friend blockEntry = new Friend(null, blocker, blocked, EFriendStatus.BLOCKED, null);
        friendRepository.save(blockEntry);
    }

    @Transactional
    public void unblockUser(UUID unblockerId, UUID unblockedId) {
        User unblocker = userService.getUserById(unblockerId);
        User unblocked = userService.getUserById(unblockedId);

        Optional<Friend> blockEntry = friendRepository.findByUserAndFriend(unblocker, unblocked);
        blockEntry.ifPresent(entry -> {
            if (entry.getStatus() == EFriendStatus.BLOCKED) {
                friendRepository.delete(entry);
            } else {
                throw new IllegalStateException("User is not blocked by you.");
            }
        });
    }

    private FriendResponse mapFriendToFriendResponse(Friend friend) {
        UserResponse user = userService.mapUserToUserResponse(friend.getUser());
        UserResponse friendUser = userService.mapUserToUserResponse(friend.getFriend());
        return new FriendResponse(friend.getId(), user, friendUser, friend.getStatus(), friend.getCreatedAt());
    }
}
