package iuh.fit.se.minizalobackend.dtos.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkReadRequest {
    private UUID groupId;
}
