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

    // Các lời mời kết bạn do current user GỬI đi nhưng chưa được chấp nhận/từ chối
    List<iuh.fit.se.minizalobackend.payload.response.FriendResponse> getSentFriendRequests(UUID userId);

    // Hủy lời mời kết bạn mà current user là người gửi
    void cancelSentFriendRequest(UUID currentUserId, UUID requestId);

    void blockUser(UUID blockerId, UUID blockedId);

    void unblockUser(UUID unblockerId, UUID unblockedId);

    // Danh sách user mà current user đã chặn (BLOCKED)
    List<iuh.fit.se.minizalobackend.payload.response.FriendResponse> getBlockedUsers(UUID userId);
}