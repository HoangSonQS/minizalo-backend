package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.dtos.request.InitiateCallRequest;
import iuh.fit.se.minizalobackend.dtos.request.InitiateGroupCallRequest;
import iuh.fit.se.minizalobackend.dtos.request.AcceptCallRequest;
import iuh.fit.se.minizalobackend.dtos.request.JoinGroupCallRequest;
import iuh.fit.se.minizalobackend.dtos.request.LeaveGroupCallRequest;
import iuh.fit.se.minizalobackend.dtos.request.RejectCallRequest;
import iuh.fit.se.minizalobackend.dtos.request.EndCallRequest;
import iuh.fit.se.minizalobackend.dtos.response.InitiateCallResponse;
import iuh.fit.se.minizalobackend.dtos.response.AcceptCallResponse;
import iuh.fit.se.minizalobackend.dtos.response.CallTokenResponse;
import iuh.fit.se.minizalobackend.dtos.response.GroupCallEventPayload;
import iuh.fit.se.minizalobackend.dtos.response.GroupCallSessionResponse;
import iuh.fit.se.minizalobackend.dtos.response.IncomingCallPayload;
import iuh.fit.se.minizalobackend.models.CallParticipant;
import iuh.fit.se.minizalobackend.models.CallSession;
import iuh.fit.se.minizalobackend.models.ECallParticipantStatus;
import iuh.fit.se.minizalobackend.models.ECallStatus;
import iuh.fit.se.minizalobackend.models.User;
import iuh.fit.se.minizalobackend.repository.CallParticipantRepository;
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
    private final CallParticipantRepository callParticipantRepository;
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
        session.setConversationId(request.getConversationId());
        session.setChannelName(request.getConversationId().toString());
        session.setCallerId(callerId);
        session.setReceiverId(request.getReceiverId());
        session.setCallType(request.getCallType());
        session.setStatus(ECallStatus.PENDING);
        session.setGroupCall(false);
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

    /**
     * Group call:
     * - Host initiates, gets token for itself.
     * - Receivers are INVITED via WS INCOMING_GROUP_CALL.
     * - Each participant obtains their own token when joining via /join.
     */
    @PostMapping("/initiate-group")
    public ResponseEntity<GroupCallSessionResponse> initiateGroupCall(
            @Valid @RequestBody InitiateGroupCallRequest request,
            Principal principal) {

        UUID hostId = UUID.fromString(getUserIdFromPrincipal(principal));
        UUID conversationId = request.getConversationId();
        List<UUID> receiverIds = request.getReceiverIds() != null ? request.getReceiverIds() : List.of();

        // Session channel: use session id later; but we need channel now -> random UUID (stable for session)
        UUID channelId = UUID.randomUUID();

        CallSession session = new CallSession();
        session.setConversationId(conversationId);
        session.setChannelName(channelId.toString());
        session.setCallerId(hostId);
        // DB hiện tại đang có constraint NOT NULL cho receiver_id.
        // Với group call, receiverId không dùng; set tạm hostId để tránh insert fail.
        session.setReceiverId(hostId);
        session.setCallType(request.getCallType());
        session.setStatus(ECallStatus.PENDING);
        session.setGroupCall(true);
        session = callSessionRepository.save(session);

        // Create participants: host JOINED, others INVITED
        CallParticipant host = new CallParticipant();
        host.setCallSession(session);
        host.setUserId(hostId);
        host.setStatus(ECallParticipantStatus.JOINED);
        host.setJoinedAt(LocalDateTime.now());
        callParticipantRepository.save(host);

        for (UUID rid : receiverIds) {
            if (rid == null) continue;
            if (rid.equals(hostId)) continue;
            CallParticipant p = new CallParticipant();
            p.setCallSession(session);
            p.setUserId(rid);
            p.setStatus(ECallParticipantStatus.INVITED);
            callParticipantRepository.save(p);
        }

        // Token for host (per-user)
        CallTokenResponse agoraToken = agoraService.generateToken(
                UUID.fromString(session.getChannelName()),
                hostId.toString(),
                session.getCallType()
        );

        // WS: send INCOMING_GROUP_CALL to each receiver
        User hostUser = userService.getUserById(hostId).orElse(null);
        IncomingCallPayload signal = IncomingCallPayload.builder()
                .callSessionId(session.getId().toString())
                .channelName(session.getChannelName())
                .callType(session.getCallType())
                .caller(IncomingCallPayload.CallerInfo.builder()
                        .id(hostId)
                        .name(hostUser != null ? hostUser.getDisplayName() : "Người dùng")
                        .avatar(hostUser != null ? hostUser.getAvatarUrl() : null)
                        .build())
                .build();

        Map<String, Object> payload = Map.of("type", "INCOMING_GROUP_CALL", "payload", signal);
        for (UUID rid : receiverIds) {
            if (rid == null || rid.equals(hostId)) continue;
            messagingTemplate.convertAndSend("/topic/call/" + rid, payload);
        }

        // Gửi 1 message log vào phòng chat để mọi thành viên thấy "Nhấn để tham gia"
        // (kể cả người không nằm trong receiverIds). Lưu messageId để khi kết thúc update in-place.
        iuh.fit.se.minizalobackend.models.MessageDynamo logMsg = sendCallLogMessage(session, "STARTED");
        if (logMsg != null && logMsg.getMessageId() != null) {
            session.setMessageId(logMsg.getMessageId());
            callSessionRepository.save(session);
        }

        List<GroupCallSessionResponse.ParticipantDto> participants = callParticipantRepository
                .findByCallSession_Id(session.getId())
                .stream()
                .map(p -> GroupCallSessionResponse.ParticipantDto.builder()
                        .userId(p.getUserId())
                        .status(p.getStatus().name())
                        .build())
                .toList();

        return ResponseEntity.ok(GroupCallSessionResponse.builder()
                .token(agoraToken.getToken())
                .appId(agoraToken.getAppId())
                .channelName(agoraToken.getChannelName())
                .callSessionId(session.getId().toString())
                .expireAt(agoraToken.getExpireAt())
                .callType(session.getCallType())
                .hostId(hostId)
                .conversationId(conversationId)
                .participants(participants)
                .build());
    }

    @PostMapping("/join")
    public ResponseEntity<GroupCallSessionResponse> joinGroupCall(
            @Valid @RequestBody JoinGroupCallRequest request,
            Principal principal) {
        UUID userId = UUID.fromString(getUserIdFromPrincipal(principal));
        CallSession session = callSessionRepository.findById(request.getCallSessionId())
                .orElseThrow(() -> new RuntimeException("Call session not found"));

        if (!session.isGroupCall()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (session.getStatus() == ECallStatus.ENDED || session.getStatus() == ECallStatus.CANCELLED || session.getStatus() == ECallStatus.REJECTED) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        CallParticipant me = callParticipantRepository
                .findByCallSession_IdAndUserId(session.getId(), userId)
                .orElse(null);

        // Cho phép join từ message/header ngay cả khi không được mời (miễn là call còn sống).
        // TODO: kiểm tra user có thuộc conversationId để chặt chẽ hơn.
        if (me == null) {
            me = new CallParticipant();
            me.setCallSession(session);
            me.setUserId(userId);
        }

        me.setStatus(ECallParticipantStatus.JOINED);
        me.setJoinedAt(LocalDateTime.now());
        callParticipantRepository.save(me);

        if (session.getStatus() == ECallStatus.PENDING) {
            session.setStatus(ECallStatus.ACTIVE);
            session.setStartedAt(LocalDateTime.now());
            callSessionRepository.save(session);
        }

        CallTokenResponse agoraToken = agoraService.generateToken(
                UUID.fromString(session.getChannelName()),
                userId.toString(),
                session.getCallType()
        );

        // Broadcast PARTICIPANT_JOINED
        List<GroupCallSessionResponse.ParticipantDto> participants = callParticipantRepository
                .findByCallSession_Id(session.getId())
                .stream()
                .map(p -> GroupCallSessionResponse.ParticipantDto.builder()
                        .userId(p.getUserId())
                        .status(p.getStatus().name())
                        .build())
                .toList();

        GroupCallEventPayload evt = GroupCallEventPayload.builder()
                .eventType("PARTICIPANT_JOINED")
                .callSessionId(session.getId().toString())
                .channelName(session.getChannelName())
                .conversationId(session.getConversationId())
                .hostId(session.getCallerId())
                .callType(session.getCallType())
                .actorId(userId)
                .actorStatus(ECallParticipantStatus.JOINED.name())
                .participants(participants)
                .build();

        broadcastGroupEvent(session.getId(), evt);

        return ResponseEntity.ok(GroupCallSessionResponse.builder()
                .token(agoraToken.getToken())
                .appId(agoraToken.getAppId())
                .channelName(agoraToken.getChannelName())
                .callSessionId(session.getId().toString())
                .expireAt(agoraToken.getExpireAt())
                .callType(session.getCallType())
                .hostId(session.getCallerId())
                .conversationId(session.getConversationId())
                .participants(participants)
                .build());
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leaveGroupCall(
            @Valid @RequestBody LeaveGroupCallRequest request,
            Principal principal) {
        UUID userId = UUID.fromString(getUserIdFromPrincipal(principal));
        CallSession session = callSessionRepository.findById(request.getCallSessionId())
                .orElseThrow(() -> new RuntimeException("Call session not found"));
        if (!session.isGroupCall()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();

        CallParticipant me = callParticipantRepository
                .findByCallSession_IdAndUserId(session.getId(), userId)
                .orElseThrow(() -> new RuntimeException("Not participant"));

        // If user leaves before joining (still INVITED), treat as DECLINED.
        if (me.getStatus() == ECallParticipantStatus.INVITED) {
            me.setStatus(ECallParticipantStatus.DECLINED);
        } else {
            me.setStatus(ECallParticipantStatus.LEFT);
        }
        me.setLeftAt(LocalDateTime.now());
        callParticipantRepository.save(me);

        List<GroupCallSessionResponse.ParticipantDto> participants = callParticipantRepository
                .findByCallSession_Id(session.getId())
                .stream()
                .map(p -> GroupCallSessionResponse.ParticipantDto.builder()
                        .userId(p.getUserId())
                        .status(p.getStatus().name())
                        .build())
                .toList();

        GroupCallEventPayload evt = GroupCallEventPayload.builder()
                .eventType("PARTICIPANT_LEFT")
                .callSessionId(session.getId().toString())
                .channelName(session.getChannelName())
                .conversationId(session.getConversationId())
                .hostId(session.getCallerId())
                .callType(session.getCallType())
                .actorId(userId)
                .actorStatus(me.getStatus().name())
                .participants(participants)
                .build();
        broadcastGroupEvent(session.getId(), evt);

        // Auto-end CHỈ khi KHÔNG CÒN AI JOINED.
        // (Trước đây dùng <2 gây bug: N=3, B chưa accept, A leave → host còn 1 JOINED → end → B không kịp vào.)
        // Việc "1 người cô đơn trong call" là trách nhiệm của họ: họ có thể bấm "Thoát" hoặc host bấm "Kết thúc cho tất cả".
        long joinedCount = callParticipantRepository
                .findByCallSession_IdAndStatus(session.getId(), ECallParticipantStatus.JOINED)
                .size();
        if (joinedCount == 0 && session.getStatus() != ECallStatus.ENDED) {
            endGroupSessionInternal(session, userId, "AUTO_END_EMPTY");
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/end-group")
    public ResponseEntity<Void> endGroupCall(
            @Valid @RequestBody EndCallRequest request,
            Principal principal) {
        UUID userId = UUID.fromString(getUserIdFromPrincipal(principal));
        CallSession session = callSessionRepository.findById(request.getCallSessionId())
                .orElseThrow(() -> new RuntimeException("Call session not found"));
        if (!session.isGroupCall()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();

        // Only host can end explicitly
        if (!session.getCallerId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        endGroupSessionInternal(session, userId, "HOST_END");
        return ResponseEntity.ok().build();
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
        // 4. Thông báo cho chính Receiver (các thiết bị khác) để tắt màn hình chuông/rung
        messagingTemplate.convertAndSend(
                "/topic/call/" + receiverId,
                Map.of("type", "TAKEN", "callSessionId", session.getId().toString())
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

        // Multi-device safety: chỉ cho phép REJECT khi đang PENDING và user là receiver.
        // Nếu đã ACTIVE (thiết bị khác đã nhấc máy), thì ignore để không làm rớt cuộc gọi đang chạy.
        if (session.getStatus() != ECallStatus.PENDING) {
            return ResponseEntity.ok().build();
        }
        if (session.getReceiverId() == null || !session.getReceiverId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ECallStatus oldStatus = session.getStatus();
        session.setStatus(ECallStatus.REJECTED);
        session.setEndedAt(LocalDateTime.now());
        callSessionRepository.save(session);

        // Gửi tin nhắn log vào Chat Room
        // Nếu reject khi đang PENDING -> Coi là MISSED (Cuộc gọi nhỡ)
        sendCallLogMessage(session, oldStatus == ECallStatus.PENDING ? "MISSED" : "REJECTED");

        // Broadcast cho cả 2 phía (và cả các thiết bị khác nhau của cùng user)
        messagingTemplate.convertAndSend(
                "/topic/call/" + session.getCallerId(),
                Map.of("type", "REJECTED", "callSessionId", session.getId().toString())
        );
        if (session.getReceiverId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/call/" + session.getReceiverId(),
                    Map.of("type", "REJECTED", "callSessionId", session.getId().toString())
            );
        }

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
        // Đồng bộ cho caller (trường hợp caller đăng nhập nhiều thiết bị)
        messagingTemplate.convertAndSend(
                "/topic/call/" + session.getCallerId().toString(),
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

        // Gửi tin nhắn log vào phòng chat
        if (oldStatus == ECallStatus.ACTIVE) {
            sendCallLogMessage(session, "ENDED");
        } else if (oldStatus == ECallStatus.PENDING) {
            // Còn đổ chuông / đang kết nối mà kết thúc → giống huỷ/không nhấc máy → một tin MISSED trong thread
            sendCallLogMessage(session, "MISSED");
        }

        // Broadcast cho cả 2 phía (và các thiết bị khác nhau)
        messagingTemplate.convertAndSend(
                "/topic/call/" + session.getCallerId(),
                Map.of("type", "ENDED", "callSessionId", session.getId().toString(), "duration", session.getDurationSeconds())
        );
        if (session.getReceiverId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/call/" + session.getReceiverId(),
                    Map.of("type", "ENDED", "callSessionId", session.getId().toString(), "duration", session.getDurationSeconds())
            );
        }

        return ResponseEntity.ok().build();
    }

    private iuh.fit.se.minizalobackend.models.MessageDynamo sendCallLogMessage(CallSession session, String status) {
        String convIdStr = resolveConversationIdString(session);
        if (convIdStr == null) return null;

        String content = buildCallLogContent(session, status);
        if (content == null) return null;

        iuh.fit.se.minizalobackend.payload.request.ChatMessageRequest messageRequest = new iuh.fit.se.minizalobackend.payload.request.ChatMessageRequest();
        // IMPORTANT: receiverId ở ChatMessageRequest là conversationId (room) — KHÔNG phải Agora channelName.
        messageRequest.setReceiverId(convIdStr);
        messageRequest.setType("CALL_" + session.getCallType().name()); // CALL_VOICE hoặc CALL_VIDEO
        messageRequest.setContent(content);
        try {
            return messageService.processMessage(messageRequest, session.getCallerId().toString());
        } catch (Exception e) {
            log.error("Failed to send call log message", e);
            return null;
        }
    }

    /**
     * Update in-place tin nhắn call log (dùng cho GROUP CALL khi kết thúc).
     * Nếu session không có messageId (vd dữ liệu cũ trước khi migration) → fallback tạo tin mới
     * để không làm mất record trong UI.
     */
    private void updateCallLogMessage(CallSession session, String finalStatus) {
        String convIdStr = resolveConversationIdString(session);
        if (convIdStr == null) return;

        String newContent = buildCallLogContent(session, finalStatus);
        if (newContent == null) return;

        String msgId = session.getMessageId();
        if (msgId == null || msgId.isBlank()) {
            // Fallback: chưa có tin STARTED (data legacy) → tạo tin mới cho đỡ mất log.
            log.warn("[updateCallLogMessage] session {} thiếu messageId, fallback sendCallLogMessage", session.getId());
            sendCallLogMessage(session, finalStatus);
            return;
        }
        iuh.fit.se.minizalobackend.models.MessageDynamo updated =
                messageService.updateMessageContent(convIdStr, msgId, newContent, null);
        if (updated == null) {
            // Tin gốc đã bị xoá (hiếm) → tạo tin mới để bảo toàn lịch sử.
            log.warn("[updateCallLogMessage] message {} không tồn tại, fallback send", msgId);
            sendCallLogMessage(session, finalStatus);
        }
    }

    private String resolveConversationIdString(CallSession session) {
        String convIdStr = session.getConversationId() != null ? session.getConversationId().toString() : null;
        if (convIdStr == null) {
            try {
                convIdStr = java.util.UUID.fromString(session.getChannelName()).toString();
            } catch (Exception e) {
                log.warn("Skip call log message: missing conversationId for session {}", session.getId());
                return null;
            }
        }
        return convIdStr;
    }

    private String buildCallLogContent(CallSession session, String status) {
        Map<String, Object> contentMap = new java.util.HashMap<>();
        contentMap.put("status", status);
        contentMap.put("duration", session.getDurationSeconds());
        contentMap.put("callType", session.getCallType().name());
        contentMap.put("callerId", session.getCallerId().toString());
        contentMap.put("conversationId", session.getConversationId() != null ? session.getConversationId().toString() : null);
        contentMap.put("callSessionId", session.getId().toString());
        contentMap.put("isGroupCall", session.isGroupCall());
        if (!session.isGroupCall() && session.getReceiverId() != null) {
            contentMap.put("receiverId", session.getReceiverId().toString());
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(contentMap);
        } catch (Exception e) {
            log.error("Failed to build call log content", e);
            return null;
        }
    }

    private void broadcastGroupEvent(UUID callSessionId, GroupCallEventPayload evt) {
        List<CallParticipant> parts = callParticipantRepository.findByCallSession_Id(callSessionId);
        for (CallParticipant p : parts) {
            messagingTemplate.convertAndSend("/topic/call/" + p.getUserId(), Map.of("type", evt.getEventType(), "payload", evt));
        }
    }

    private void endGroupSessionInternal(CallSession session, UUID actorId, String reason) {
        ECallStatus oldStatus = session.getStatus();
        session.setStatus(ECallStatus.ENDED);
        session.setEndedAt(LocalDateTime.now());
        callSessionRepository.save(session);

        // GROUP CALL: update in-place tin log STARTED → ENDED/MISSED (1 bubble duy nhất).
        // Nhờ vậy UI không còn "Tham gia" sau khi session đã chết, và hiển thị đúng duration.
        if (oldStatus == ECallStatus.ACTIVE) {
            updateCallLogMessage(session, "ENDED");
        } else if (oldStatus == ECallStatus.PENDING) {
            updateCallLogMessage(session, "MISSED");
        }

        List<GroupCallSessionResponse.ParticipantDto> participants = callParticipantRepository
                .findByCallSession_Id(session.getId())
                .stream()
                .map(p -> GroupCallSessionResponse.ParticipantDto.builder()
                        .userId(p.getUserId())
                        .status(p.getStatus().name())
                        .build())
                .toList();

        GroupCallEventPayload evt = GroupCallEventPayload.builder()
                .eventType("GROUP_CALL_ENDED")
                .callSessionId(session.getId().toString())
                .channelName(session.getChannelName())
                .conversationId(session.getConversationId())
                .hostId(session.getCallerId())
                .callType(session.getCallType())
                .actorId(actorId)
                .actorStatus(reason)
                .participants(participants)
                .build();
        broadcastGroupEvent(session.getId(), evt);
    }

    @GetMapping("/pending")
    public ResponseEntity<IncomingCallPayload> getPendingCall(Principal principal) {
        UUID userId = UUID.fromString(getUserIdFromPrincipal(principal));

        // 1) Group invite pending (prefer group over direct on reconnect)
        List<CallParticipant> invites = callParticipantRepository.findPendingGroupInvites(userId);
        if (!invites.isEmpty()) {
            CallParticipant inv = invites.get(0);
            CallSession session = inv.getCallSession();
            User caller = userService.getUserById(session.getCallerId()).orElse(null);

            inv.setDelivered(true);
            callParticipantRepository.save(inv);

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

            return ResponseEntity.ok(payload);
        }

        // 2) Direct pending (existing): find PENDING within 60 seconds
        Optional<CallSession> pending = callSessionRepository.findPendingCallForReceiver(
                userId,
                LocalDateTime.now().minusSeconds(60)
        );

        if (pending.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        CallSession session = pending.get();
        User caller = userService.getUserById(session.getCallerId()).orElse(null);

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
