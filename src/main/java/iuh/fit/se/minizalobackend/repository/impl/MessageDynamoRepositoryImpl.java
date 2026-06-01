package iuh.fit.se.minizalobackend.repository.impl;

import iuh.fit.se.minizalobackend.dtos.response.PaginatedMessageResult;
import iuh.fit.se.minizalobackend.dtos.response.SearchMessageResponse;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.repository.MessageDynamoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class MessageDynamoRepositoryImpl implements MessageDynamoRepository {

    private final DynamoDbTable<MessageDynamo> messageTable;

    public MessageDynamoRepositoryImpl(DynamoDbEnhancedClient enhancedClient) {
        this.messageTable = enhancedClient.table("messages", TableSchema.fromBean(MessageDynamo.class));
    }

    @Override
    public void save(MessageDynamo message) {
        messageTable.putItem(message);
    }

    @Override
    public void deleteAllByRoomId(String chatRoomId) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(chatRoomId).build());
        var pages = messageTable.query(r -> r.queryConditional(queryConditional));
        for (Page<MessageDynamo> page : pages) {
            for (MessageDynamo msg : page.items()) {
                Key key = Key.builder()
                        .partitionValue(msg.getChatRoomId())
                        .sortValue(msg.getCreatedAt())
                        .build();
                messageTable.deleteItem(key);
            }
        }
    }

    @Override
    public boolean deleteByMessageId(String chatRoomId, String messageId) {
        Optional<MessageDynamo> message = getMessage(chatRoomId, messageId);
        if (message.isEmpty()) {
            return false;
        }
        MessageDynamo msg = message.get();
        Key key = Key.builder()
                .partitionValue(msg.getChatRoomId())
                .sortValue(msg.getCreatedAt())
                .build();
        messageTable.deleteItem(key);
        return true;
    }

    @Override
    public PaginatedMessageResult getMessagesByRoomId(String chatRoomId, String lastEvaluatedKey, int limit) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(chatRoomId).build());

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit)
                .scanIndexForward(false);

        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            Map<String, AttributeValue> startKey = deserializeExclusiveStartKey(lastEvaluatedKey);
            requestBuilder.exclusiveStartKey(startKey);
        }

        var pagedResult = messageTable.query(requestBuilder.build());
        Optional<Page<MessageDynamo>> firstPage = pagedResult.stream().findFirst();

        if (firstPage.isPresent()) {
            Page<MessageDynamo> page = firstPage.get();
            List<MessageDynamo> messages = page.items();
            String newLastEvaluatedKey = serializeExclusiveStartKey(page.lastEvaluatedKey());
            return new PaginatedMessageResult(messages, newLastEvaluatedKey);
        } else {
            return new PaginatedMessageResult(Collections.emptyList(), null);
        }
    }

    @Override
    public List<MessageDynamo> getMessagesBetweenDates(String chatRoomId, String startTime, String endTime) {
        QueryConditional queryConditional = QueryConditional.sortBetween(
                Key.builder().partitionValue(chatRoomId).sortValue(startTime).build(),
                Key.builder().partitionValue(chatRoomId).sortValue(endTime).build()
        );

        var pages = messageTable.query(r -> r.queryConditional(queryConditional).scanIndexForward(true));
        
        List<MessageDynamo> results = new ArrayList<>();
        for (Page<MessageDynamo> page : pages) {
            results.addAll(page.items());
        }
        return results;
    }

    @Override
    public Optional<MessageDynamo> getMessage(String chatRoomId, String messageId) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(chatRoomId).build());

        var pagedResult = messageTable.query(r -> r.queryConditional(queryConditional));

        return pagedResult.items().stream()
                .filter(m -> messageId.equals(m.getMessageId()))
                .findFirst();
    }

    @Override
    public PaginatedMessageResult getPinnedMessagesByRoomId(String chatRoomId, String lastEvaluatedKey, int limit) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(chatRoomId).build());

        Expression filterExpression = Expression.builder()
                .expression("pinned = :pinned")
                .putExpressionValue(":pinned", AttributeValue.builder().bool(true).build())
                .build();

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .filterExpression(filterExpression)
                .scanIndexForward(false);

        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            Map<String, AttributeValue> startKey = deserializeExclusiveStartKey(lastEvaluatedKey);
            requestBuilder.exclusiveStartKey(startKey);
        }

        var pagedResult = messageTable.query(requestBuilder.build());
        List<MessageDynamo> resultMessages = new ArrayList<>();
        Map<String, AttributeValue> finalLastEvaluatedKey = null;

        for (Page<MessageDynamo> page : pagedResult) {
            for (MessageDynamo msg : page.items()) {
                resultMessages.add(msg);
                if (resultMessages.size() == limit) {
                    break;
                }
            }
            if (resultMessages.size() == limit) {
                MessageDynamo lastMessage = resultMessages.get(resultMessages.size() - 1);
                Map<String, AttributeValue> key = new HashMap<>();
                key.put("chatRoomId", AttributeValue.builder().s(lastMessage.getChatRoomId()).build());
                key.put("createdAt", AttributeValue.builder().s(lastMessage.getCreatedAt()).build());
                finalLastEvaluatedKey = key;
                break;
            }
            if (page.lastEvaluatedKey() == null) {
                finalLastEvaluatedKey = null;
                break;
            }
            finalLastEvaluatedKey = page.lastEvaluatedKey();
        }

        String newLastEvaluatedKey = serializeExclusiveStartKey(finalLastEvaluatedKey);
        return new PaginatedMessageResult(resultMessages, newLastEvaluatedKey);
    }

    @Override
    public long countPinnedMessages(String chatRoomId) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(chatRoomId).build());

        Expression filterExpression = Expression.builder()
                .expression("pinned = :pinned")
                .putExpressionValue(":pinned", AttributeValue.builder().bool(true).build())
                .build();

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .filterExpression(filterExpression)
                .build();

        PageIterable<MessageDynamo> pages = messageTable.query(request);
        long count = 0L;
        for (Page<MessageDynamo> page : pages) {
            count += page.items().size();
        }
        return count;
    }

    @Override
    public long countMessagesBySender(String chatRoomId, String senderId) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(chatRoomId).build());

        Expression filterExpression = Expression.builder()
                .expression("senderId = :senderId")
                .putExpressionValue(":senderId", AttributeValue.builder().s(senderId).build())
                .build();

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .filterExpression(filterExpression)
                .build();

        PageIterable<MessageDynamo> pages = messageTable.query(request);
        long count = 0L;
        for (Page<MessageDynamo> page : pages) {
            count += page.items().size();
        }
        return count;
    }

    @Override
    public long countUnreadMessages(String chatRoomId, String userId, String lastReadAtIso) {
        QueryConditional queryConditional;
        if (lastReadAtIso != null && !lastReadAtIso.isBlank()) {
            queryConditional = QueryConditional
                    .sortGreaterThan(Key.builder()
                            .partitionValue(chatRoomId)
                            .sortValue(lastReadAtIso)
                            .build());
        } else {
            queryConditional = QueryConditional
                    .keyEqualTo(Key.builder().partitionValue(chatRoomId).build());
        }

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .build();

        PageIterable<MessageDynamo> pages = messageTable.query(request);
        long count = 0L;
        for (Page<MessageDynamo> page : pages) {
            for (MessageDynamo msg : page.items()) {
                // Bỏ qua tin của chính mình, tin hệ thống
                if (userId.equals(msg.getSenderId())) continue;
                if ("system".equals(msg.getSenderId())) continue;
                if ("SYSTEM".equals(msg.getType()) || "PIN_NOTIFICATION".equals(msg.getType())) continue;
                if (msg.isPrivacyBlocked()) continue;

                if (msg.getReadBy() == null || !msg.getReadBy().contains(userId)) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public SearchMessageResponse searchMessages(String chatRoomId, String query, int limit, String lastEvaluatedKey,
            String senderId, String fromDateInclusive, String toDateInclusive) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(chatRoomId).build());

        boolean hasQuery = query != null && !query.isBlank();
        boolean hasSender = senderId != null && !senderId.isBlank();
        boolean hasFrom = fromDateInclusive != null && !fromDateInclusive.isBlank();
        boolean hasTo = toDateInclusive != null && !toDateInclusive.isBlank();

        if (!hasQuery && !hasSender && !hasFrom && !hasTo) {
            return new SearchMessageResponse(Collections.emptyList(), null, false, 0);
        }

        List<String> parts = new ArrayList<>();
        Expression.Builder exprBuilder = Expression.builder();

        if (hasQuery) {
            parts.add("contains(content, :query)");
            exprBuilder.putExpressionValue(":query", AttributeValue.builder().s(query.trim()).build());
        }
        if (hasSender) {
            parts.add("senderId = :senderId");
            exprBuilder.putExpressionValue(":senderId", AttributeValue.builder().s(senderId.trim()).build());
        }
        if (hasFrom) {
            parts.add("createdAt >= :fromDate");
            exprBuilder.putExpressionValue(":fromDate", AttributeValue.builder().s(fromDateInclusive.trim()).build());
        }
        if (hasTo) {
            parts.add("createdAt <= :toDate");
            exprBuilder.putExpressionValue(":toDate", AttributeValue.builder().s(toDateInclusive.trim()).build());
        }

        exprBuilder.expression(parts.stream().collect(Collectors.joining(" AND ")));
        Expression filterExpression = exprBuilder.build();

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .filterExpression(filterExpression)
                .limit(limit)
                .scanIndexForward(false);

        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            Map<String, AttributeValue> startKey = deserializeExclusiveStartKey(lastEvaluatedKey);
            requestBuilder.exclusiveStartKey(startKey);
        }

        var pagedResult = messageTable.query(requestBuilder.build());
        Optional<Page<MessageDynamo>> firstPage = pagedResult.stream().findFirst();

        if (firstPage.isPresent()) {
            Page<MessageDynamo> page = firstPage.get();
            List<MessageDynamo> messages = page.items();
            String newLastEvaluatedKey = serializeExclusiveStartKey(page.lastEvaluatedKey());
            return new SearchMessageResponse(messages, newLastEvaluatedKey, newLastEvaluatedKey != null,
                    messages.size());
        } else {
            return new SearchMessageResponse(Collections.emptyList(), null, false, 0);
        }
    }

    @Override
    public java.util.Optional<MessageDynamo> getOldestUnreadMessage(String chatRoomId, String userId, String lastReadAtIso) {
        QueryConditional queryConditional;
        if (lastReadAtIso != null && !lastReadAtIso.isBlank()) {
            queryConditional = QueryConditional.sortGreaterThan(
                    Key.builder().partitionValue(chatRoomId).sortValue(lastReadAtIso).build());
        } else {
            queryConditional = QueryConditional.keyEqualTo(
                    Key.builder().partitionValue(chatRoomId).build());
        }

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(true) // cũ nhất trước
                .build();

        PageIterable<MessageDynamo> pages = messageTable.query(request);
        int totalInspected = 0;
        for (Page<MessageDynamo> page : pages) {
            for (MessageDynamo msg : page.items()) {
                totalInspected++;
                // Bỏ qua tin hệ thống, tin của chính user
                if ("system".equals(msg.getSenderId())) continue;
                if ("SYSTEM".equals(msg.getType()) || "PIN_NOTIFICATION".equals(msg.getType())) continue;
                if (userId.equals(msg.getSenderId())) continue;
                if (msg.isPrivacyBlocked()) continue;
                
                List<String> readBy = msg.getReadBy();
                if (readBy == null || !readBy.contains(userId)) {
                    log.info("[getOldestUnreadMessage] Found target: {} at inspected count: {}", msg.getMessageId(), totalInspected);
                    return java.util.Optional.of(msg);
                }
            }
        }
        log.info("[getOldestUnreadMessage] No unread message found after inspecting {} messages.", totalInspected);
        return java.util.Optional.empty();
    }

    @Override
    public UnreadContextResult getMessagesAroundTarget(String chatRoomId, String targetCreatedAt,
            int countBefore, int countAfter) {

        // --- Phần 1: Lấy các tin nhắn MOỜI HƠN target (messagesAfter) ---
        // Trong DynamoDB inverted FlatList, "after" = createdAt > targetCreatedAt, scan xuôi
        QueryConditional afterQuery = QueryConditional.sortGreaterThan(
                Key.builder().partitionValue(chatRoomId).sortValue(targetCreatedAt).build());

        QueryEnhancedRequest afterRequest = QueryEnhancedRequest.builder()
                .queryConditional(afterQuery)
                .scanIndexForward(true) // tăng dần, lấy countAfter tin gần target nhất
                .limit(countAfter + 1) // +1 để biết hasMoreAfter
                .build();

        List<MessageDynamo> rawAfter = new ArrayList<>();
        for (Page<MessageDynamo> p : messageTable.query(afterRequest)) {
            rawAfter.addAll(p.items());
            if (rawAfter.size() >= countAfter + 1) break;
        }
        boolean hasMoreAfter = rawAfter.size() > countAfter;
        List<MessageDynamo> messagesAfter = rawAfter.subList(0, Math.min(countAfter, rawAfter.size()));
        // Đảo ngược để mới nhất ở đầu (phù hợp inverted FlatList)
        Collections.reverse(messagesAfter);

        // --- Phần 2: Lấy các tin nhắn CŨ HƠN target (messagesBefore) ---
        QueryConditional beforeQuery = QueryConditional.sortLessThan(
                Key.builder().partitionValue(chatRoomId).sortValue(targetCreatedAt).build());

        QueryEnhancedRequest beforeRequest = QueryEnhancedRequest.builder()
                .queryConditional(beforeQuery)
                .scanIndexForward(false) // giảm dần, lấy countBefore tin gần target nhất
                .limit(countBefore + 1) // +1 để biết hasMoreBefore
                .build();

        List<MessageDynamo> rawBefore = new ArrayList<>();
        for (Page<MessageDynamo> p : messageTable.query(beforeRequest)) {
            rawBefore.addAll(p.items());
            if (rawBefore.size() >= countBefore + 1) break;
        }
        boolean hasMoreBefore = rawBefore.size() > countBefore;
        // rawBefore đang xếp giảm dần (mới rồi cũ), được giữ nguyîn (phù hợp inverted FlatList: từ mới → cũ)
        List<MessageDynamo> messagesBefore = rawBefore.subList(0, Math.min(countBefore, rawBefore.size()));

        return new UnreadContextResult(messagesBefore, messagesAfter, hasMoreBefore, hasMoreAfter);
    }

    private String serializeExclusiveStartKey(Map<String, AttributeValue> key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        String chatRoomId = key.get("chatRoomId").s();
        String createdAt = key.get("createdAt").s();
        String combined = chatRoomId + ":" + createdAt;
        return Base64.getEncoder().encodeToString(combined.getBytes());
    }

    private Map<String, AttributeValue> deserializeExclusiveStartKey(String base64Key) {
        if (base64Key == null || base64Key.isEmpty()) {
            return null;
        }
        byte[] decodedBytes = Base64.getDecoder().decode(base64Key);
        String[] parts = new String(decodedBytes).split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid lastEvaluatedKey format");
        }

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("chatRoomId", AttributeValue.builder().s(parts[0]).build());
        key.put("createdAt", AttributeValue.builder().s(parts[1]).build());
        return key;
    }
}
