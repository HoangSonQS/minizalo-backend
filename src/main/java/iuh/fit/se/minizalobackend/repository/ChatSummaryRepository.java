package iuh.fit.se.minizalobackend.repository;

import iuh.fit.se.minizalobackend.models.ChatSummary;
import java.util.List;

public interface ChatSummaryRepository {
    void save(ChatSummary summary);
    List<ChatSummary> getSummariesByRoomId(String roomId);
}
