package iuh.fit.se.minizalobackend.payload.request;

import iuh.fit.se.minizalobackend.models.Attachment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class ChatMessageRequest {
    @NotBlank(message = "Receiver ID is required")
    private String receiverId;

    @Size(max = 1000, message = "Content must be less than 1000 characters")
    private String content;

    private List<Attachment> attachments;

    private String replyToMessageId;

    /** Ghi đè loại tin (ví dụ FOLDER khi gửi cả thư mục một lần). Nếu null thì suy từ attachment. */
    private String type;
}
