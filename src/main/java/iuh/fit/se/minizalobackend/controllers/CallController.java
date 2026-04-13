package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.dtos.request.InitiateCallRequest;
import iuh.fit.se.minizalobackend.dtos.request.AcceptCallRequest;
import iuh.fit.se.minizalobackend.dtos.request.RejectCallRequest;
import iuh.fit.se.minizalobackend.dtos.request.EndCallRequest;
import iuh.fit.se.minizalobackend.dtos.response.InitiateCallResponse;
import iuh.fit.se.minizalobackend.dtos.response.AcceptCallResponse;
import iuh.fit.se.minizalobackend.dtos.response.CallTokenResponse;
import iuh.fit.se.minizalobackend.dtos.response.IncomingCallPayload;
import iuh.fit.se.minizalobackend.models.CallSession;
import iuh.fit.se.minizalobackend.models.ECallStatus;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.repository.CallSessionRepository;
import iuh.fit.se.minizalobackend.security.services.UserDetailsImpl;
import iuh.fit.se.minizalobackend.services.AgoraService;
import iuh.fit.se.minizalobackend.services.MessageService;
import iuh.fit.se.minizalobackend.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/call")
@RequiredArgsConstructor
@Slf4j
public class CallController {

    private final AgoraService agoraService;
    private final CallSessionRepository callSessionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;
    private final MessageService messageService;

    @PostMapping("/initiate")
    public ResponseEntity<InitiateCallResponse> initiateCall(
            @Valid @RequestBody InitiateCallRequest request,
            Principal principal) {
        
        UUID callerId = UUID.fromString(getUserIdFromPrincipal(principal));
        log.info("Initiating call from {} to {} in conversation {}", callerId, request.getReceiverId(), request.getConversationId());

        // 1. Lưu CallSession vào DB (PostgreSQL)
        CallSession session = new CallSession();
        session.setChannelName(request.getConversationId().toString());
        session.setCallerId(callerId);
        session.setReceiverId(request.getReceiverId());
        session.setCallType(request.getCallType());
        session.setStatus(ECallStatus.PENDING);
        session = callSessionRepository.save(session);

        // 2. Tạo Token cho Caller
        CallTokenResponse agoraToken = agoraService.generateToken(
                (java.util.UUID) request.getConversationId(),
                callerId.toString(),
                request.getCallType()
        );

        // 3. Gửi tín hiệu WebSocket tới Receiver
        User caller = userService.getUserById(callerId).orElse(null);
        IncomingCallPayload signal = IncomingCallPayload.builder()
                .callSessionId(session.getId().toString())
                .channelName(session.getChannelName())
                .callType(session.getCallType())
                .caller(IncomingCallPayload.CallerInfo.builder()
                        .id(callerId)
                        .name(caller != null ? caller.getDisplayName() : "Người dùng")
                        .avatar(caller != null ? caller.getAvatarUrl() : null)
                        .build())
                .build();

        String receiverUuid = request.getReceiverId().toString();
        
        // Debug check to detect ID swap from Frontend
        if (receiverUuid.equals(callerId.toString())) {
            log.error("=== SIGNALING ERROR === Receiver ID is IDENTICAL to Caller ID: {}. Check Frontend logic!", receiverUuid);
        }

        log.info("=== SIGNALING === Sending INCOMING signal to /topic/call/{}", receiverUuid);
        
        Map<String, Object> payload = Map.of("type", "INCOMING", "payload", signal);
        
        messagingTemplate.convertAndSend(
                "/topic/call/" + receiverUuid,
                payload
        );

        log.info("=== SIGNALING === INCOMING sent to /topic/call/{}", receiverUuid);

        return ResponseEntity.ok(InitiateCallResponse.builder()
                .token(agoraToken.getToken())
                .appId(agoraToken.getAppId())
                .channelName(agoraToken.getChannelName())
                .callSessionId(session.getId().toString())
                .expireAt(agoraToken.getExpireAt())
                .callType(agoraToken.getCallType())
                .build());
    }

    @PostMapping("/accept")
    public ResponseEntity<AcceptCallResponse> acceptCall(
            @Valid @RequestBody AcceptCallRequest request,
            Principal principal) {
        
        UUID receiverId = UUID.fromString(getUserIdFromPrincipal(principal));
        CallSession session = callSessionRepository.findById(request.getCallSessionId())
                .orElseThrow(() -> new RuntimeException("Call session not found"));

        if (!session.getReceiverId().equals(receiverId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (session.getStatus() != ECallStatus.PENDING) {
            log.warn("=== CALL === Cannot accept session {} - status is {} (expected PENDING)", 
                     request.getCallSessionId(), session.getStatus());
            return ResponseEntity.badRequest()
                    .body(null);
        }

        // 1. Update session status
        session.setStatus(ECallStatus.ACTIVE);
        session.setStartedAt(LocalDateTime.now());
        callSessionRepository.save(session);

        // 2. Tạo Token cho Receiver
        CallTokenResponse agoraToken = agoraService.generateToken(
                java.util.UUID.fromString(session.getChannelName()),
                receiverId.toString(),
                session.getCallType()
        );

        // 3. Thông báo cho Caller qua WebSocket
        messagingTemplate.convertAndSend(
                "/topic/call/" + session.getCallerId().toString(),
                Map.of("type", "ACCEPTED", "callSessionId", session.getId().toString())
        );

        return ResponseEntity.ok(AcceptCallResponse.builder()
                .token(agoraToken.getToken())
                .appId(agoraToken.getAppId())
                .channelName(agoraToken.getChannelName())
                .expireAt(agoraToken.getExpireAt())
                .callType(agoraToken.getCallType())
                .build());
    }

    @PostMapping("/reject")
    public ResponseEntity<Void> rejectCall(
            @Valid @RequestBody RejectCallRequest request,
            Principal principal) {
        
        UUID userId = UUID.fromString(getUserIdFromPrincipal(principal));
        CallSession session = callSessionRepository.findById(request.getCallSessionId())
                .orElseThrow(() -> new RuntimeException("Call session not found"));

        ECallStatus oldStatus = session.getStatus();
        session.setStatus(ECallStatus.REJECTED);
        session.setEndedAt(LocalDateTime.now());
        callSessionRepository.save(session);

        // Gửi tin nhắn log vào Chat Room
        // Nếu reject khi đang PENDING -> Coi là MISSED (Cuộc gọi nhỡ)
        sendCallLogMessage(session, oldStatus == ECallStatus.PENDING ? "MISSED" : "REJECTED");

        UUID otherId = userId.equals(session.getCallerId()) ? session.getReceiverId() : session.getCallerId();
        messagingTemplate.convertAndSend(
                "/topic/call/" + otherId.toString(),
                Map.of("type", "REJECTED", "callSessionId", session.getId().toString())
        );

        return ResponseEntity.ok().build();
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelCall(
            @Valid @RequestBody RejectCallRequest request, // Dùng chung DTO vì chỉ cần sessionId
            Principal principal) {
        
        UUID callerId = UUID.fromString(getUserIdFromPrincipal(principal));
        CallSession session = callSessionRepository.findById(request.getCallSessionId())
                .orElseThrow(() -> new RuntimeException("Call session not found"));

        if (!session.getCallerId().equals(callerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ECallStatus oldStatus = session.getStatus();
        session.setStatus(ECallStatus.CANCELLED);
        session.setEndedAt(LocalDateTime.now());
        callSessionRepository.save(session);

        // Gửi tin nhắn log vào Chat Room
        // Nếu cancel khi đang PENDING -> Coi là MISSED (Cuộc gọi nhỡ)
        sendCallLogMessage(session, oldStatus == ECallStatus.PENDING ? "MISSED" : "CANCELLED");

        messagingTemplate.convertAndSend(
                "/topic/call/" + session.getReceiverId().toString(),
                Map.of("type", "CANCELLED", "callSessionId", session.getId().toString())
        );

        return ResponseEntity.ok().build();
    }

    @PostMapping("/end")
    public ResponseEntity<Void> endCall(
            @Valid @RequestBody EndCallRequest request,
            Principal principal) {
        
        UUID userId = UUID.fromString(getUserIdFromPrincipal(principal));
        CallSession session = callSessionRepository.findById(request.getCallSessionId())
                .orElseThrow(() -> new RuntimeException("Call session not found"));

        if (session.getStatus() == ECallStatus.ENDED || session.getStatus() == ECallStatus.REJECTED || session.getStatus() == ECallStatus.CANCELLED) {
            return ResponseEntity.ok().build();
        }

        ECallStatus oldStatus = session.getStatus();
        session.setStatus(ECallStatus.ENDED);
        session.setEndedAt(LocalDateTime.now());
        callSessionRepository.save(session);

        // Gửi tin nhắn log vào Chat Room (chỉ khi cuộc gọi đã ACTIVE)
        if (oldStatus == ECallStatus.ACTIVE) {
            sendCallLogMessage(session, "ENDED");
        }

        UUID otherId = userId.equals(session.getCallerId()) ? session.getReceiverId() : session.getCallerId();
        messagingTemplate.convertAndSend(
                "/topic/call/" + otherId.toString(),
                Map.of("type", "ENDED", "callSessionId", session.getId().toString(), "duration", session.getDurationSeconds())
        );

        return ResponseEntity.ok().build();
    }

    private void sendCallLogMessage(CallSession session, String status) {
        iuh.fit.se.minizalobackend.payload.request.ChatMessageRequest messageRequest = new iuh.fit.se.minizalobackend.payload.request.ChatMessageRequest();
        messageRequest.setReceiverId(session.getChannelName()); // Gửi vào conversationId (room)
        messageRequest.setType("CALL_" + session.getCallType().name()); // CALL_VOICE hoặc CALL_VIDEO
        
        // Nội dung tin nhắn dạng JSON để FE dễ xử lý
        Map<String, Object> contentMap = Map.of(
            "status", status,
            "duration", session.getDurationSeconds(),
            "callType", session.getCallType().name(),
            "callerId", session.getCallerId().toString(),
            "receiverId", session.getReceiverId().toString()
        );
        
        try {
            messageRequest.setContent(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(contentMap));
            messageService.processMessage(messageRequest, session.getCallerId().toString());
        } catch (Exception e) {
            log.error("Failed to send call log message", e);
        }
    }

    @GetMapping("/pending")
    public ResponseEntity<IncomingCallPayload> getPendingCall(Principal principal) {
        UUID userId = UUID.fromString(getUserIdFromPrincipal(principal));
        
        // Tìm cuộc gọi PENDING trong 60 giây gần nhất
        Optional<CallSession> pending = callSessionRepository.findPendingCallForReceiver(
                userId, 
                LocalDateTime.now().minusSeconds(60)
        );

        if (pending.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        CallSession session = pending.get();
        User caller = userService.getUserById(session.getCallerId()).orElse(null);

        // Đánh dấu đã gửi tín hiệu để tránh lặp lại (idempotent)
        session.setDelivered(true);
        callSessionRepository.save(session);

        IncomingCallPayload payload = IncomingCallPayload.builder()
                .callSessionId(session.getId().toString())
                .channelName(session.getChannelName())
                .callType(session.getCallType())
                .caller(IncomingCallPayload.CallerInfo.builder()
                        .id(session.getCallerId())
                        .name(caller != null ? caller.getDisplayName() : "Người dùng")
                        .avatar(caller != null ? caller.getAvatarUrl() : null)
                        .build())
                .build();

        log.info("=== SIGNALING === Found and DELIVERED pending call for user: {}. Session ID: {}", userId, session.getId());
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/history")
    public ResponseEntity<List<CallSession>> getHistory(Principal principal) {
        UUID userId = UUID.fromString(getUserIdFromPrincipal(principal));
        List<CallSession> history = callSessionRepository.findByCallerIdOrReceiverIdOrderByCreatedAtDesc(userId, userId);
        return ResponseEntity.ok(history);
    }

    /**
     * Pattern extracted from ChatController to maintain consistency in userId
     * extraction from JWT.
     */
    private String getUserIdFromPrincipal(Principal principal) {
        if (principal == null) {
            throw new IllegalStateException("Session not authenticated – JWT may be expired or missing");
        }
        if (principal instanceof UsernamePasswordAuthenticationToken) {
            Object p = ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
            if (p instanceof UserDetailsImpl) {
                return ((UserDetailsImpl) p).getId().toString();
            }
        }
        return principal.getName();
    }
}
