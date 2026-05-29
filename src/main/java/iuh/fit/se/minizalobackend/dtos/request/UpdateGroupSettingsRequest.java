package iuh.fit.se.minizalobackend.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UpdateGroupSettingsRequest {
    @NotNull(message = "Group ID is required")
    private UUID groupId;

    private Boolean allowMemberChangeName;
    private Boolean allowMemberPin;
    private Boolean allowMemberCreatePoll;
    private Boolean allowMemberSendMessage;
    private Boolean requireApproval;
    private Boolean allowNewMemberReadHistory;
    private Boolean allowJoinByLink;
}
