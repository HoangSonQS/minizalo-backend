package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.dtos.response.CallTokenResponse;
import iuh.fit.se.minizalobackend.models.ECallType;

import java.util.UUID;

public interface AgoraService {
    CallTokenResponse generateToken(UUID conversationId, String userUuid, ECallType callType);
}
