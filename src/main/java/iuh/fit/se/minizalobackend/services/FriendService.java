package iuh.fit.se.minizalobackend.services;

import java.util.List;
import java.util.UUID;

public interface FriendService {
    iuh.fit.se.minizalobackend.payload.response.FriendResponse sendFriendRequest(UUID senderId, UUID receiverId);

    iuh.fit.se.minizalobackend.payload.response.FriendResponse acceptFriendRequest(UUID currentUserId, UUID requestId);

    void rejectFriendRequest(UUID currentUserId, UUID requestId);

    void deleteFriend(UUID currentUserId, UUID friendIdToDelete);

    List<iuh.fit.se.minizalobackend.payload.response.FriendResponse> getFriendsList(UUID userId);

    List<iuh.fit.se.minizalobackend.payload.response.FriendResponse> getPendingFriendRequests(UUID userId);

    void blockUser(UUID blockerId, UUID blockedId);

    void unblockUser(UUID unblockerId, UUID unblockedId);
}