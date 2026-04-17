package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.dtos.request.AddPollOptionRequest;
import iuh.fit.se.minizalobackend.dtos.request.CreatePollRequest;
import iuh.fit.se.minizalobackend.dtos.request.PollVoteRequest;
import iuh.fit.se.minizalobackend.dtos.response.PollOptionResponse;
import iuh.fit.se.minizalobackend.dtos.response.PollResponse;
import iuh.fit.se.minizalobackend.dtos.response.PollVoteResponse;
import iuh.fit.se.minizalobackend.exception.ResourceNotFoundException;
import iuh.fit.se.minizalobackend.models.*;
import iuh.fit.se.minizalobackend.repository.*;
import iuh.fit.se.minizalobackend.services.PollService;
import iuh.fit.se.minizalobackend.services.MessageService;
import iuh.fit.se.minizalobackend.services.MinioService;
import iuh.fit.se.minizalobackend.utils.AppConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PollServiceImpl implements PollService {

    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final GroupSettingsRepository groupSettingsRepository;
    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MinioService minioService;

    @Override
    @Transactional
    public PollResponse createPoll(CreatePollRequest request, User creator) {
        ChatRoom room = chatRoomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        RoomMember member = roomMemberRepository.findByRoomAndUser(room, creator)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this room"));

        if (room.getType() == ERoomType.GROUP) {
            GroupSettings settings = groupSettingsRepository.findByGroupId(room.getId()).orElse(null);
            if (settings != null && !settings.isAllowMemberCreatePoll()) {
                boolean isOwner = room.getCreatedBy().getId().equals(creator.getId());
                boolean isAdmin = member.getRole() == ERoomRole.ADMIN;
                if (!isOwner && !isAdmin) {
                    throw new IllegalArgumentException("Only admins can create polls in this group.");
                }
            }
        }

        Poll poll = Poll.builder()
                .question(request.getQuestion())
                .allowMultipleChoices(request.isAllowMultipleChoices())
                .allowAddOptions(request.isAllowAddOptions())
                .room(room)
                .createdBy(creator)
                .closed(false)
                .build();

        final Poll pollRef = poll;
        List<PollOption> options = request.getOptions().stream()
                .filter(o -> o != null && !o.trim().isEmpty())
                .map(text -> PollOption.builder()
                        .text(text)
                        .poll(pollRef)
                        .createdBy(creator)
                        .build())
                .collect(Collectors.toList());

        poll.setOptions(options);
        Poll savedPoll = pollRepository.save(poll);

        // Save a POLL message in DynamoDB
        MessageDynamo sysMsg = new MessageDynamo();
        // Dùng luôn pollId làm messageId để các SYSTEM message có thể trỏ về poll bằng replyToMessageId.
        sysMsg.setMessageId(savedPoll.getId().toString());
        sysMsg.setChatRoomId(room.getId().toString());
        sysMsg.setSenderId(creator.getId().toString());
        sysMsg.setSenderName(creator.getDisplayName() != null ? creator.getDisplayName() : creator.getUsername());
        sysMsg.setContent(savedPoll.getId().toString()); // store pollId in content
        sysMsg.setType("POLL");
        sysMsg.setCreatedAt(Instant.now().toString());
        messageService.saveMessage(sysMsg);

        // Broadcast message to WebSocket
        String destinationMsg = "/topic/chat/" + room.getId().toString();
        messagingTemplate.convertAndSend(destinationMsg, sysMsg);

        PollResponse response = buildPollResponse(savedPoll);
        // Also broadcast the new poll data
        broadcastPollUpdate(response);
        publishPollSystemMessage(
                room,
                creator,
                creator.getDisplayName() != null ? creator.getDisplayName() : creator.getUsername(),
                " đã tạo cuộc bình chọn mới: " + savedPoll.getQuestion(),
                savedPoll.getId().toString()
        );

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PollResponse> getPollsInRoom(UUID roomId, User viewer) {
        if (!roomMemberRepository.existsByRoom_IdAndUser_Id(roomId, viewer.getId())) {
            throw new IllegalArgumentException("You are not a member of this room");
        }
        return pollRepository.findByRoomIdOrderByCreatedAtDesc(roomId).stream()
                .map(this::buildPollResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PollResponse addOptionToPoll(AddPollOptionRequest request, User initiator) {
        Poll poll = pollRepository.findById(request.getPollId())
                .orElseThrow(() -> new ResourceNotFoundException("Poll not found"));

        if (!roomMemberRepository.existsByRoom_IdAndUser_Id(poll.getRoom().getId(), initiator.getId())) {
            throw new IllegalArgumentException("You are not a member of this room");
        }

        if (poll.isClosed()) {
            throw new IllegalArgumentException("Poll is already closed");
        }

        if (!poll.isAllowAddOptions()) {
            throw new IllegalArgumentException("This poll does not allow adding new options");
        }

        PollOption newOption = PollOption.builder()
                .poll(poll)
                .text(request.getText())
                .createdBy(initiator)
                .build();
        pollOptionRepository.save(newOption);
        poll.getOptions().add(newOption);

        PollResponse response = buildPollResponse(poll);
        broadcastPollUpdate(response);

        return response;
    }

    @Override
    @Transactional
    public PollResponse votePoll(PollVoteRequest request, User voter) {
        Poll poll = pollRepository.findById(request.getPollId())
                .orElseThrow(() -> new ResourceNotFoundException("Poll not found"));

        if (!roomMemberRepository.existsByRoom_IdAndUser_Id(poll.getRoom().getId(), voter.getId())) {
            throw new IllegalArgumentException("You are not a member of this room");
        }

        if (poll.isClosed()) {
            throw new IllegalArgumentException("Poll is already closed");
        }

        if (!poll.isAllowMultipleChoices() && request.getOptionIds().size() > 1) {
            throw new IllegalArgumentException("This poll only allows a single choice");
        }

        // Delete all previous votes by this user for this poll
        pollVoteRepository.deleteByPollIdAndUserId(poll.getId(), voter.getId());

        // Cast new votes
        for (UUID optionId : request.getOptionIds()) {
            PollOption option = pollOptionRepository.findById(optionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Option not found: " + optionId));
            if (!option.getPoll().getId().equals(poll.getId())) {
                throw new IllegalArgumentException("Option " + optionId + " does not belong to poll " + poll.getId());
            }

            PollVote vote = PollVote.builder()
                    .option(option)
                    .user(voter)
                    .build();
            pollVoteRepository.save(vote);
            // Must manually add to list for the buildPollResponse to reflect immediately if using the same EntityManager session
            option.getVotes().add(vote);
        }

        // Must flush or re-fetch for accurate representation if need be.
        pollVoteRepository.flush();
        // Clear caches using fetch or manually
        Poll updatedPoll = pollRepository.findById(poll.getId()).get();

        PollResponse response = buildPollResponse(updatedPoll);
        broadcastPollUpdate(response);
        publishPollSystemMessage(
                poll.getRoom(),
                voter,
                voter.getDisplayName() != null ? voter.getDisplayName() : voter.getUsername(),
                " tham gia cuộc bình chọn: " + poll.getQuestion(),
                poll.getId().toString()
        );

        return response;
    }

    @Override
    @Transactional
    public PollResponse closePoll(UUID pollId, User initiator) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new ResourceNotFoundException("Poll not found"));

        RoomMember member = roomMemberRepository.findByRoomAndUser_Id(poll.getRoom(), initiator.getId())
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this room"));

        boolean isPollCreator = poll.getCreatedBy().getId().equals(initiator.getId());
        boolean isOwner = poll.getRoom().getCreatedBy() != null && poll.getRoom().getCreatedBy().getId().equals(initiator.getId());
        boolean isAdmin = member.getRole() == ERoomRole.ADMIN;

        if (!isPollCreator && !isOwner && !isAdmin) {
            throw new IllegalArgumentException("Only poll creator or admins can close this poll.");
        }

        poll.setClosed(true);
        poll = pollRepository.save(poll);

        PollResponse response = buildPollResponse(poll);
        broadcastPollUpdate(response);
        publishPollSystemMessage(
                poll.getRoom(),
                initiator,
                initiator.getDisplayName() != null ? initiator.getDisplayName() : initiator.getUsername(),
                " khóa bình chọn: " + poll.getQuestion(),
                poll.getId().toString()
        );

        return response;
    }

    @Override
    @Transactional
    public void deletePoll(UUID pollId, User initiator) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new ResourceNotFoundException("Poll not found"));

        RoomMember member = roomMemberRepository.findByRoomAndUser_Id(poll.getRoom(), initiator.getId())
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this room"));

        boolean isPollCreator = poll.getCreatedBy().getId().equals(initiator.getId());
        boolean isOwner = poll.getRoom().getCreatedBy() != null && poll.getRoom().getCreatedBy().getId().equals(initiator.getId());
        boolean isAdmin = member.getRole() == ERoomRole.ADMIN;

        if (!isPollCreator && !isOwner && !isAdmin) {
            throw new IllegalArgumentException("Only poll creator or admins can delete this poll.");
        }

        pollRepository.delete(poll);
        // Could also broadcast a delete event so clients remove it from UI
        String destination = "/topic/chat/" + poll.getRoom().getId().toString() + "/polls";
        messagingTemplate.convertAndSend(destination, java.util.Map.of("deleted", pollId.toString()));
    }

    private PollResponse buildPollResponse(Poll poll) {
        return PollResponse.builder()
                .id(poll.getId().toString())
                .question(poll.getQuestion())
                .allowMultipleChoices(poll.isAllowMultipleChoices())
                .allowAddOptions(poll.isAllowAddOptions())
                .closed(poll.isClosed())
                .createdById(poll.getCreatedBy().getId().toString())
                .createdByName(poll.getCreatedBy().getDisplayName() != null ? poll.getCreatedBy().getDisplayName() : poll.getCreatedBy().getUsername())
                .roomId(poll.getRoom().getId().toString())
                .createdAt(poll.getCreatedAt())
                .updatedAt(poll.getUpdatedAt())
                .options(poll.getOptions().stream().map(o -> PollOptionResponse.builder()
                        .id(o.getId().toString())
                        .text(o.getText())
                        .createdById(o.getCreatedBy().getId().toString())
                        .votes(o.getVotes().stream().map(v -> PollVoteResponse.builder()
                                .id(v.getId().toString())
                                .userId(v.getUser().getId().toString())
                                .username(v.getUser().getUsername())
                                .displayName(v.getUser().getDisplayName())
                                .avatarUrl(minioService.ensurePublicUrl(v.getUser().getAvatarUrl()))
                                .votedAt(v.getVotedAt())
                                .build()).collect(Collectors.toList()))
                        .build()).collect(Collectors.toList()))
                .build();
    }

    private void broadcastPollUpdate(PollResponse pollResponse) {
        String destination = "/topic/chat/" + pollResponse.getRoomId() + "/polls";
        messagingTemplate.convertAndSend(destination, pollResponse);
    }

    private void publishPollSystemMessage(ChatRoom room, User actor, String actorName, String contentSuffix, String replyToPollMessageId) {
        MessageDynamo systemMsg = new MessageDynamo();
        systemMsg.setMessageId(UUID.randomUUID().toString());
        systemMsg.setChatRoomId(room.getId().toString());
        systemMsg.setSenderId("SYSTEM");
        systemMsg.setSenderName("SYSTEM");
        systemMsg.setContent(actorName + contentSuffix);
        systemMsg.setType(AppConstants.MESSAGE_TYPE_SYSTEM);
        systemMsg.setReplyToMessageId(replyToPollMessageId);
        systemMsg.setCreatedAt(Instant.now().toString());

        MessageDynamo savedMessage = messageService.saveMessage(systemMsg);

        messagingTemplate.convertAndSend("/topic/chat/" + room.getId().toString(),
                java.util.Map.of(
                        "messageId", savedMessage.getMessageId(),
                        "chatRoomId", room.getId().toString(),
                        "senderId", "SYSTEM",
                        "senderUsername", "SYSTEM",
                        "content", savedMessage.getContent(),
                        "type", AppConstants.MESSAGE_TYPE_SYSTEM,
                        "replyToMessageId", replyToPollMessageId,
                        "timestamp", savedMessage.getCreatedAt()
                ));
    }
}
