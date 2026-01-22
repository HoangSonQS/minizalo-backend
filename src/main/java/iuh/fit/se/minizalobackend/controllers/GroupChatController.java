package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.dtos.request.AddMembersRequest;
import iuh.fit.se.minizalobackend.dtos.request.CreateGroupRequest;
import iuh.fit.se.minizalobackend.dtos.request.RemoveMembersRequest;
import iuh.fit.se.minizalobackend.dtos.request.SendGroupMessageRequest;
import iuh.fit.se.minizalobackend.dtos.request.UpdateGroupRequest;
import iuh.fit.se.minizalobackend.dtos.response.GroupResponse;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.payload.response.MessageResponse; // Import MessageResponse
import iuh.fit.se.minizalobackend.security.services.UserDetailsImpl;
import iuh.fit.se.minizalobackend.services.GroupService;
import iuh.fit.se.minizalobackend.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/group")
@RequiredArgsConstructor
public class GroupChatController {

    private final GroupService groupService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User creator = userService.getUserById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        GroupResponse newGroup = groupService.createGroup(request, creator);
        return new ResponseEntity<>(newGroup, HttpStatus.CREATED);
    }

    @PostMapping("/members")
    public ResponseEntity<GroupResponse> addMembers(
            @Valid @RequestBody AddMembersRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User initiator = userService.getUserById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        GroupResponse updatedGroup = groupService.addMembersToGroup(request.getGroupId(), request.getMemberIds(),
                initiator);
        return ResponseEntity.ok(updatedGroup);
    }

    @DeleteMapping("/members")
    public ResponseEntity<GroupResponse> removeMembers(
            @Valid @RequestBody RemoveMembersRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User initiator = userService.getUserById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        GroupResponse updatedGroup = groupService.removeMembersFromGroup(request.getGroupId(), request.getMemberIds(),
                initiator);
        return ResponseEntity.ok(updatedGroup);
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponse> getGroupInfo(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User viewer = userService.getUserById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        GroupResponse groupInfo = groupService.getGroupInfo(groupId, viewer);
        return ResponseEntity.ok(groupInfo);
    }

    @GetMapping("/my-groups")
    public ResponseEntity<List<GroupResponse>> getUsersGroups(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User currentUser = userService.getUserById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<GroupResponse> userGroups = groupService.getUsersGroups(currentUser);
        return ResponseEntity.ok(userGroups);
    }

    @PostMapping("/message")
    public ResponseEntity<Void> sendGroupMessage(
            @Valid @RequestBody SendGroupMessageRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User sender = userService.getUserById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        groupService.sendGroupMessage(request, sender);
        return ResponseEntity.ok().build();
    }

    @PutMapping
    public ResponseEntity<GroupResponse> updateGroup(
            @Valid @RequestBody UpdateGroupRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User initiator = userService.getUserById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        GroupResponse updatedGroup = groupService.updateGroup(request, initiator);
        return ResponseEntity.ok(updatedGroup);
    }

    @PostMapping("/leave/{groupId}")
    public ResponseEntity<MessageResponse> leaveGroup(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User currentUser = userService.getUserById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        MessageResponse response = groupService.leaveGroup(groupId, currentUser);
        return ResponseEntity.ok(response);
    }
}