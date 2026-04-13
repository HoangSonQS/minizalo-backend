package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.payload.response.AcceptFriendRequestResponse;
import iuh.fit.se.minizalobackend.payload.response.FriendResponse;
import iuh.fit.se.minizalobackend.payload.response.UserProfileResponse;
import iuh.fit.se.minizalobackend.models.EFriendStatus;
import iuh.fit.se.minizalobackend.models.Friend;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.repository.FriendCategoryAssignmentRepository;
import iuh.fit.se.minizalobackend.repository.FriendRepository;
import iuh.fit.se.minizalobackend.services.ChatRoomService;
import iuh.fit.se.minizalobackend.services.FriendService;
import iuh.fit.se.minizalobackend.services.UserService;
import lombok.RequiredArgsConstructor;
import iuh.fit.se.minizalobackend.services.MessageService;
import iuh.fit.se.minizalobackend.payload.request.ChatMessageRequest;
import iuh.fit.se.minizalobackend.payload.request.FriendRequest;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final FriendRepository friendRepository;
    private final FriendCategoryAssignmentRepository assignmentRepository;
    private final UserService userService;
    private final ChatRoomService chatRoomService;
    private final MessageService messageService;

    private static String normalizeInviteSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UNKNOWN";
        }
        String u = raw.trim().toUpperCase();
        if ("CHAT_WINDOW".equals(u) || "PHONE_SEARCH".equals(u)) {
            return u;
        }
        return "UNKNOWN";
    }

    @Override
    @Transactional
    public FriendResponse sendFriendRequest(UUID senderId, FriendRequest request) {
        UUID receiverId = request.getFriendId();
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("Cannot send friend request to self.");
        }

        User sender = userService.getUserById(senderId)
                .orElseThrow(() -> new UsernameNotFoundException("Sender not found with id: " + senderId));
        User receiver = userService.getUserById(receiverId)
                .orElseThrow(() -> new UsernameNotFoundException("Receiver not found with id: " + receiverId));

        Optional<Friend> existingFriendship = friendRepository.findByUserAndFriend(sender, receiver);
        if (existingFriendship.isPresent()) {
            throw new IllegalStateException("Friend request already sent or they are already friends.");
        }

        Optional<Friend> existingReverseFriendship = friendRepository.findByUserAndFriend(receiver, sender);
        if (existingReverseFriendship.isPresent()
                && existingReverseFriendship.get().getStatus() == EFriendStatus.PENDING) {
            throw new IllegalStateException("You have a pending friend request from this user. Accept it instead.");
        }

        String msg = request.getInviteMessage() == null ? null : request.getInviteMessage().trim();
        if (msg != null && msg.isEmpty()) {
            msg = null;
        }
        if (msg != null && msg.length() > 150) {
            msg = msg.substring(0, 150);
        }
        String src = normalizeInviteSource(request.getInviteSource());
        boolean hideTimeline = Boolean.TRUE.equals(request.getHideMyTimelineFromFriend());

        Friend friendRequest = new Friend(null, sender, receiver, EFriendStatus.PENDING, null, msg, src, hideTimeline);
        return mapFriendToFriendResponse(friendRepository.save(friendRequest));
    }

    @Override
    @Transactional
    public AcceptFriendRequestResponse acceptFriendRequest(UUID currentUserId, UUID requestId) {
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
                EFriendStatus.ACCEPTED, null, null, null, false);
        friendRepository.save(reciprocalFriendship);

        // Create direct chat room
        iuh.fit.se.minizalobackend.dtos.response.ChatRoomResponse room = chatRoomService
                .createDirectChat(friendRequest.getUser(), friendRequest.getFriend());
        ChatMessageRequest helloRequest = new ChatMessageRequest();
        helloRequest.setReceiverId(room.getId().toString());
        helloRequest.setContent("Hello");
        messageService.processMessage(helloRequest, friendRequest.getFriend().getId().toString());

        return new AcceptFriendRequestResponse(mapFriendToFriendResponse(acceptedRequest), room);

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

        // Xóa thẻ phân loại 2 chiều (A gán cho B, và B gán cho A nếu có)
        assignmentRepository.deleteByOwnerAndTarget(currentUser, friendUser);
        assignmentRepository.deleteByOwnerAndTarget(friendUser, currentUser);

        // Delete friendship from current user to friend
        Optional<Friend> friendship1 = friendRepository.findByUserAndFriend(currentUser, friendUser);
        friendship1.ifPresent(friendRepository::delete);

        // Delete reciprocal friendship from friend to current user
        Optional<Friend> friendship2 = friendRepository.findByUserAndFriend(friendUser, currentUser);
        friendship2.ifPresent(friendRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendResponse> getFriendsList(UUID userId) {
        //
        User currentUser = userService.getUserById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
        // Danh bạ cần hiển thị cả bạn bè đang ACCEPTED lẫn những người đã bị chặn tin
        // nhắn
        // (BLOCKED) để chặn chỉ ảnh hưởng tới chat, không làm mất bạn khỏi danh sách.
        var accepted = friendRepository.findByUserAndStatus(currentUser, EFriendStatus.ACCEPTED);
        var blocked = friendRepository.findByUserAndStatus(currentUser, EFriendStatus.BLOCKED);

        return Stream.concat(accepted.stream(), blocked.stream())
                .map(this::mapFriendToFriendResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendResponse> getPendingFriendRequests(UUID userId) {
        User currentUser = userService.getUserById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
        // Các lời mời kết bạn mà current user LÀ NGƯỜI NHẬN (friend) và đang ở
        // trạng thái PENDING
        return friendRepository.findByFriendAndStatus(currentUser, EFriendStatus.PENDING).stream()
                .map(this::mapFriendToFriendResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendResponse> getSentFriendRequests(UUID userId) {
        User currentUser = userService.getUserById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
        // Các lời mời kết bạn mà current user LÀ NGƯỜI GỬI (user) và đang PENDING
        return friendRepository.findByUserAndStatus(currentUser, EFriendStatus.PENDING).stream()
                .map(this::mapFriendToFriendResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void cancelSentFriendRequest(UUID currentUserId, UUID requestId) {
        Friend friendRequest = friendRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found."));

        // Chỉ cho phép hủy nếu current user là NGƯỜI GỬI và request vẫn đang pending
        if (!friendRequest.getUser().getId().equals(currentUserId)) {
            throw new SecurityException("You are not authorized to cancel this request.");
        }
        if (friendRequest.getStatus() != EFriendStatus.PENDING) {
            throw new IllegalStateException("Friend request is not pending.");
        }

        friendRepository.delete(friendRequest);
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

        // Check if already blocked
        Optional<Friend> existingBlock = friendRepository.findByUserAndFriend(blocker, blocked);
        if (existingBlock.isPresent() && existingBlock.get().getStatus() == EFriendStatus.BLOCKED) {
            throw new IllegalArgumentException("User is already blocked.");
        }

        // If there is an existing ACCEPTED/PENDING entry from blocker -> blocked, keep
        // it
        // We create a separate BLOCKED entry. The existing friendship stays intact.
        // But if the existing entry is from blocker->blocked with ACCEPTED status,
        // we change it to BLOCKED to reuse the row.
        if (existingBlock.isPresent()) {
            existingBlock.get().setStatus(EFriendStatus.BLOCKED);
            friendRepository.save(existingBlock.get());
        } else {
            Friend blockEntry = new Friend(null, blocker, blocked, EFriendStatus.BLOCKED, null, null, null, false);
            friendRepository.save(blockEntry);
        }
        // NOTE: We do NOT delete the reverse friendship entry (blocked -> blocker)
        // so the blocked user still sees the friend in their friends list.
        // The block check in messaging will prevent communication.
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
                // Check if there's a reverse friendship entry (friend -> blocker with ACCEPTED)
                // If yes, restore this entry to ACCEPTED as well
                Optional<Friend> reverseEntry = friendRepository.findByUserAndFriend(unblocked, unblocker);
                if (reverseEntry.isPresent() && reverseEntry.get().getStatus() == EFriendStatus.ACCEPTED) {
                    // Restore the friendship - set back to ACCEPTED
                    entry.setStatus(EFriendStatus.ACCEPTED);
                    friendRepository.save(entry);
                } else {
                    // No reverse friendship exists, just delete the block entry
                    friendRepository.delete(entry);
                }
            } else {
                throw new IllegalStateException("User is not blocked by you.");
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendResponse> getBlockedUsers(UUID userId) {
        User currentUser = userService.getUserById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
        return friendRepository.findByUserAndStatus(currentUser, EFriendStatus.BLOCKED).stream()
                .map(this::mapFriendToFriendResponse)
                .collect(Collectors.toList());
    }

    // This method is private and not part of the interface, but it needs to use the
    // correct UserResponse
    private FriendResponse mapFriendToFriendResponse(Friend friend) {
        UserProfileResponse user = userService.mapUserToUserProfileResponse(friend.getUser());
        UserProfileResponse friendUser = userService.mapUserToUserProfileResponse(friend.getFriend());
        return new FriendResponse(
                friend.getId(),
                user,
                friendUser,
                friend.getStatus(),
                friend.getCreatedAt(),
                friend.getInviteMessage(),
                friend.getInviteSource(),
                Boolean.TRUE.equals(friend.getHideMyTimelineFromFriend()));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> checkBlockStatus(UUID currentUserId, UUID otherUserId) {
        User currentUser = userService.getUserById(currentUserId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + currentUserId));
        User otherUser = userService.getUserById(otherUserId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + otherUserId));

        boolean blockedByYou = false;
        boolean blockedByOther = false;
        String blockerName = null;

        Optional<Friend> youBlockOther = friendRepository.findByUserAndFriend(currentUser, otherUser);
        if (youBlockOther.isPresent() && youBlockOther.get().getStatus() == EFriendStatus.BLOCKED) {
            blockedByYou = true;
        }

        Optional<Friend> otherBlockYou = friendRepository.findByUserAndFriend(otherUser, currentUser);
        if (otherBlockYou.isPresent() && otherBlockYou.get().getStatus() == EFriendStatus.BLOCKED) {
            blockedByOther = true;
            blockerName = otherUser.getDisplayName() != null ? otherUser.getDisplayName() : otherUser.getUsername();
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("blockedByYou", blockedByYou);
        result.put("blockedByOther", blockedByOther);
        result.put("blockerName", blockerName);
        return result;
    }
}
