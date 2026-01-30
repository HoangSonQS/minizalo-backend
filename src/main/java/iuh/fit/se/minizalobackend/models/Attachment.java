package iuh.fit.se.minizalobackend.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamoDbBean
public class Attachment {
    private String url;
    private String type; // e.g., "IMAGE", "VIDEO", "DOCUMENT"
    private String filename;
    private long size;
    private String thumbnailUrl; // Optional
}
