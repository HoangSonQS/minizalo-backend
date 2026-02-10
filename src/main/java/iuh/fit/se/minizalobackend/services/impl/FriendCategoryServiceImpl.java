package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.models.FriendCategory;
import iuh.fit.se.minizalobackend.models.FriendCategoryAssignment;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.payload.request.FriendCategoryUpsertRequest;
import iuh.fit.se.minizalobackend.payload.response.FriendCategoryAssignmentResponse;
import iuh.fit.se.minizalobackend.payload.response.FriendCategoryResponse;
import iuh.fit.se.minizalobackend.repository.FriendCategoryAssignmentRepository;
import iuh.fit.se.minizalobackend.repository.FriendCategoryRepository;
import iuh.fit.se.minizalobackend.repository.FriendRepository;
import iuh.fit.se.minizalobackend.repository.UserRepository;
import iuh.fit.se.minizalobackend.services.FriendCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendCategoryServiceImpl implements FriendCategoryService {
    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final FriendCategoryRepository categoryRepository;
    private final FriendCategoryAssignmentRepository assignmentRepository;

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
    }

    private void requireAreFriends(User owner, User target) {
        // Chỉ cho phân loại nếu đang là bạn (ACCEPTED) theo chiều owner -> target hoặc target -> owner
        boolean ok = friendRepository.findByUserAndFriend(owner, target)
                .map(f -> f.getStatus() != null && f.getStatus().name().equals("ACCEPTED"))
                .orElse(false)
                || friendRepository.findByUserAndFriend(target, owner)
                .map(f -> f.getStatus() != null && f.getStatus().name().equals("ACCEPTED"))
                .orElse(false);
        if (!ok) {
            throw new IllegalStateException("You can only categorize accepted friends.");
        }
    }

    private FriendCategoryResponse mapCategory(FriendCategory c) {
        return new FriendCategoryResponse(c.getId(), c.getName(), c.getColor());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendCategoryResponse> listCategories(UUID ownerUserId) {
        User owner = requireUser(ownerUserId);
        return categoryRepository.findByOwnerOrderByCreatedAtAsc(owner).stream()
                .map(this::mapCategory)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FriendCategoryResponse createCategory(UUID ownerUserId, FriendCategoryUpsertRequest request) {
        User owner = requireUser(ownerUserId);
        FriendCategory c = new FriendCategory(null, owner, request.getName().trim(), request.getColor().trim(), null, null);
        return mapCategory(categoryRepository.save(c));
    }

    @Override
    @Transactional
    public FriendCategoryResponse updateCategory(UUID ownerUserId, UUID categoryId, FriendCategoryUpsertRequest request) {
        User owner = requireUser(ownerUserId);
        FriendCategory c = categoryRepository.findByIdAndOwner(categoryId, owner)
                .orElseThrow(() -> new IllegalArgumentException("Category not found."));
        c.setName(request.getName().trim());
        c.setColor(request.getColor().trim());
        return mapCategory(categoryRepository.save(c));
    }

    @Override
    @Transactional
    public void deleteCategory(UUID ownerUserId, UUID categoryId) {
        User owner = requireUser(ownerUserId);
        FriendCategory c = categoryRepository.findByIdAndOwner(categoryId, owner)
                .orElseThrow(() -> new IllegalArgumentException("Category not found."));

        // Xóa assignments trỏ tới category này của owner
        assignmentRepository.findByOwner(owner).stream()
                .filter(a -> a.getCategory() != null && a.getCategory().getId().equals(categoryId))
                .forEach(assignmentRepository::delete);

        categoryRepository.delete(c);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendCategoryAssignmentResponse> listAssignments(UUID ownerUserId) {
        User owner = requireUser(ownerUserId);
        return assignmentRepository.findByOwner(owner).stream()
                .map(a -> new FriendCategoryAssignmentResponse(
                        a.getTarget().getId(),
                        a.getCategory() != null ? a.getCategory().getId() : null
                ))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FriendCategoryAssignmentResponse assignCategory(UUID ownerUserId, UUID targetUserId, UUID categoryIdOrNull) {
        User owner = requireUser(ownerUserId);
        User target = requireUser(targetUserId);

        requireAreFriends(owner, target);

        // hủy phân loại
        if (categoryIdOrNull == null) {
            assignmentRepository.findByOwnerAndTarget(owner, target)
                    .ifPresent(assignmentRepository::delete);
            return new FriendCategoryAssignmentResponse(targetUserId, null);
        }

        FriendCategory category = categoryRepository.findByIdAndOwner(categoryIdOrNull, owner)
                .orElseThrow(() -> new IllegalArgumentException("Category not found."));

        FriendCategoryAssignment assignment = assignmentRepository.findByOwnerAndTarget(owner, target)
                .orElseGet(() -> new FriendCategoryAssignment(null, owner, target, category, null));
        assignment.setCategory(category);
        FriendCategoryAssignment saved = assignmentRepository.save(assignment);
        return new FriendCategoryAssignmentResponse(saved.getTarget().getId(), saved.getCategory().getId());
    }
}

