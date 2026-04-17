package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.dtos.request.AddPollOptionRequest;
import iuh.fit.se.minizalobackend.dtos.request.CreatePollRequest;
import iuh.fit.se.minizalobackend.dtos.request.PollVoteRequest;
import iuh.fit.se.minizalobackend.dtos.response.PollResponse;
import iuh.fit.se.minizalobackend.models.User;

import java.util.List;
import java.util.UUID;

public interface PollService {
    PollResponse createPoll(CreatePollRequest request, User creator);

    List<PollResponse> getPollsInRoom(UUID roomId, User viewer);

    PollResponse addOptionToPoll(AddPollOptionRequest request, User initiator);

    PollResponse votePoll(PollVoteRequest request, User voter);

    PollResponse closePoll(UUID pollId, User initiator);

    void deletePoll(UUID pollId, User initiator);
}
