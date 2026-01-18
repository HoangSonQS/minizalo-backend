package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.models.Message;
import java.util.List;

public interface MessageService {
    Message saveMessage(Message message);

    List<Message> getMessages(String conversationId, int page, int size);

    void recallMessage(String messageId);
}
