package iuh.fit.se.minizalobackend.dtos.response;

import iuh.fit.se.minizalobackend.models.MessageDynamo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchMessageResponse {
    private List<MessageDynamo> messages;
    private String lastKey; // For pagination
    private boolean hasMore;
    private int totalResults;
}
