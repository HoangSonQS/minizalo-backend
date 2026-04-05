package iuh.fit.se.minizalobackend.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamoDbBean
public class Attachment {
    private String url;
    private String type;
    private String filename;
    private long size;
    private String thumbnailUrl;

    public void setUrl(String url) { this.url = url; }
    public String getUrl() { return url; }

    public void setType(String type) { this.type = type; }
    public String getType() { return type; }

    public void setFilename(String filename) { this.filename = filename; }
    public String getFilename() { return filename; }

    public void setSize(long size) { this.size = size; }
    public long getSize() { return size; }

    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
}
