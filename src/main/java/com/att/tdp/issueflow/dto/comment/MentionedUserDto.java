package com.att.tdp.issueflow.dto.comment;

import com.att.tdp.issueflow.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentionedUserDto {

    private Long id;
    private String username;
    private String fullName;

    public static MentionedUserDto from(User user) {
        return MentionedUserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .build();
    }
}
