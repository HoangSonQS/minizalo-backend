package iuh.fit.se.minizalobackend.dtos.response;

import iuh.fit.se.minizalobackend.models.MessageDynamo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class PaginatedMessageResult {
    private List<MessageDynamo> messages;
    private String lastEvaluatedKey;
}
