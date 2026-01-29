package iuh.fit.se.minizalobackend.dtos.response;

import iuh.fit.se.minizalobackend.models.ERoomRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberResponse {
    private String userId;
    private String username;
    private String avatarUrl;
    private ERoomRole role; // ADMIN, MEMBER
}
