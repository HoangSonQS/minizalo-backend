package iuh.fit.se.minizalobackend.services.impl;

import io.agora.media.RtcTokenBuilder2;
import io.agora.media.RtcTokenBuilder2.Role;
import iuh.fit.se.minizalobackend.dtos.response.CallTokenResponse;
import iuh.fit.se.minizalobackend.models.ECallType;
import iuh.fit.se.minizalobackend.services.AgoraService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class AgoraServiceImpl implements AgoraService {

    @Value("${agora.appId}")
    private String appId;

    @Value("${agora.appCertificate}")
    private String appCertificate;

    // Token expiration time in seconds (1 hour)
    private static final int TOKEN_EXPIRATION_IN_SECONDS = 3600;
    private static final int PRIVILEGE_EXPIRATION_IN_SECONDS = 3600;

    @Override
    public CallTokenResponse generateToken(UUID conversationId, String userUuid, ECallType callType) {
        String channelName = conversationId.toString();
        log.info("Generating Agora token for channel: {}, user: {}, callType: {}", channelName, userUuid, callType);

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();
        
        // buildTokenWithUserAccount is used when UID is a String (User Account)
        String token = tokenBuilder.buildTokenWithUserAccount(
                appId,
                appCertificate,
                channelName,
                userUuid,
                Role.ROLE_PUBLISHER,
                TOKEN_EXPIRATION_IN_SECONDS,
                PRIVILEGE_EXPIRATION_IN_SECONDS
        );

        long expireAt = Instant.now().getEpochSecond() + TOKEN_EXPIRATION_IN_SECONDS;

        return CallTokenResponse.builder()
                .token(token)
                .appId(appId)
                .channelName(channelName)
                .uid(userUuid)
                .expireAt(expireAt)
                .callType(callType)
                .build();
    }
}
