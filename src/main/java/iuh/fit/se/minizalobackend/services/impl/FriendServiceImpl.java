package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.payload.response.FriendResponse;
import iuh.fit.se.minizalobackend.payload.response.UserProfileResponse;
import iuh.fit.se.minizalobackend.models.EFriendStatus;
import iuh.fit.se.minizalobackend.models.Friend;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.repository.FriendRepository;
import iuh.fit.se.minizalobackend.services.FriendService;
import iuh.fit.se.minizalobackend.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final FriendRepository friendRepository;
    private final UserService userService;

    @Override
    @Transactional
    public FriendResponse sendFriendRequest(UUID senderId, UUID receiverId) {
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("Cannot send friend request to self.");
        }

        User sender = userService.getUserById(senderId)
                .orElseThrow(() -> new UsernameNotFoundException("Sender not found with id: " + senderId));
        User receiver = userService.getUserById(receiverId)
                .orElseThrow(() -> new UsernameNotFoundException("Receiver not found with id: " + receiverId));

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

    @Override
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

    @Override
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

    @Override
    @Transactional
    public void deleteFriend(UUID currentUserId, UUID friendIdToDelete) {
        User currentUser = userService.getUserById(currentUserId)
                .orElseThrow(() -> new UsernameNotFoundException("Current user not found with id: " + currentUserId));
        User friendUser = userService.getUserById(friendIdToDelete)
                .orElseThrow(() -> new UsernameNotFoundException("Friend user not found with id: " + friendIdToDelete));

        // Delete friendship from current user to friend
        Optional<Friend> friendship1 = friendRepository.findByUserAndFriend(currentUser, friendUser);
        friendship1.ifPresent(friendRepository::delete);

        // Delete reciprocal friendship from friend to current user
        Optional<Friend> friendship2 = friendRepository.findByUserAndFriend(friendUser, currentUser);
        friendship2.ifPresent(friendRepository::delete);
    }

    @Override
    public List<FriendResponse> getFriendsList(UUID userId) {
        User currentUser = userService.getUserById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
        return friendRepository.findByUserAndStatus(currentUser, EFriendStatus.ACCEPTED).stream()
                .map(this::mapFriendToFriendResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<FriendResponse> getPendingFriendRequests(UUID userId) {
        User currentUser = userService.getUserById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
        // Find requests where current user is the friend (receiver) and status is
        // PENDING
        return friendRepository.findByFriendAndStatus(currentUser, EFriendStatus.PENDING).stream()
                .map(this::mapFriendToFriendResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void blockUser(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("Cannot block self.");
        }
        User blocker = userService.getUserById(blockerId)
                .orElseThrow(() -> new UsernameNotFoundException("Blocker not found with id: " + blockerId));
        User blocked = userService.getUserById(blockedId)
                .orElseThrow(() -> new UsernameNotFoundException("Blocked user not found with id: " + blockedId));

        // Remove any existing friendship/request between them
        friendRepository.findByUserAndFriend(blocker, blocked).ifPresent(friendRepository::delete);
        friendRepository.findByUserAndFriend(blocked, blocker).ifPresent(friendRepository::delete);

        // Create a new block entry
        Friend blockEntry = new Friend(null, blocker, blocked, EFriendStatus.BLOCKED, null);
        friendRepository.save(blockEntry);
    }

    @Override
    @Transactional
    public void unblockUser(UUID unblockerId, UUID unblockedId) {
        User unblocker = userService.getUserById(unblockerId)
                .orElseThrow(() -> new UsernameNotFoundException("Unblocker not found with id: " + unblockerId));
        User unblocked = userService.getUserById(unblockedId)
                .orElseThrow(() -> new UsernameNotFoundException("Unblocked user not found with id: " + unblockedId));

        Optional<Friend> blockEntry = friendRepository.findByUserAndFriend(unblocker, unblocked);
        blockEntry.ifPresent(entry -> {
            if (entry.getStatus() == EFriendStatus.BLOCKED) {
                friendRepository.delete(entry);
            } else {
                throw new IllegalStateException("User is not blocked by you.");
            }
        });
    }

    // This method is private and not part of the interface, but it needs to use the
    // correct UserResponse
    private FriendResponse mapFriendToFriendResponse(Friend friend) {
        UserProfileResponse user = userService.mapUserToUserProfileResponse(friend.getUser()); // Changed to
                                                                                               // UserProfileResponse
        UserProfileResponse friendUser = userService.mapUserToUserProfileResponse(friend.getFriend()); // Changed to
                                                                                                       // UserProfileResponse
        return new FriendResponse(friend.getId(), user, friendUser, friend.getStatus(), friend.getCreatedAt());
    }
}
