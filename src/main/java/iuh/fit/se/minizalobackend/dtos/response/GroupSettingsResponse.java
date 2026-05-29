package iuh.fit.se.minizalobackend.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupSettingsResponse {
    private boolean allowMemberChangeName;
    private boolean allowMemberPin;
    private boolean allowMemberCreatePoll;
    private boolean allowMemberSendMessage;
    private boolean requireApproval;
    private boolean allowNewMemberReadHistory;
    private boolean allowJoinByLink;
    private String joinLink;
}
