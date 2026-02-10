package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.payload.request.FriendCategoryUpsertRequest;
import iuh.fit.se.minizalobackend.payload.response.FriendCategoryAssignmentResponse;
import iuh.fit.se.minizalobackend.payload.response.FriendCategoryResponse;

import java.util.List;
import java.util.UUID;

public interface FriendCategoryService {
    List<FriendCategoryResponse> listCategories(UUID ownerUserId);
    FriendCategoryResponse createCategory(UUID ownerUserId, FriendCategoryUpsertRequest request);
    FriendCategoryResponse updateCategory(UUID ownerUserId, UUID categoryId, FriendCategoryUpsertRequest request);
    void deleteCategory(UUID ownerUserId, UUID categoryId);

    List<FriendCategoryAssignmentResponse> listAssignments(UUID ownerUserId);
    FriendCategoryAssignmentResponse assignCategory(UUID ownerUserId, UUID targetUserId, UUID categoryIdOrNull);
}

