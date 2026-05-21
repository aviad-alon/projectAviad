package com.att.tdp.issueflow.dto.comment;

import com.att.tdp.issueflow.entity.Comment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentResponse {

    private Long id;
    private Long ticketId;
    private Long authorId;
    private String content;
    private LocalDateTime createdAt;
    private List<MentionedUserDto> mentionedUsers;
    private Long version;

    public static CommentResponse from(Comment comment) {
        List<MentionedUserDto> mentioned = comment.getMentionedUsers() == null
                ? List.of()
                : comment.getMentionedUsers().stream()
                        .map(MentionedUserDto::from)
                        .collect(Collectors.toList());

        return CommentResponse.builder()
                .id(comment.getId())
                .ticketId(comment.getTicket() != null ? comment.getTicket().getId() : null)
                .authorId(comment.getAuthor() != null ? comment.getAuthor().getId() : null)
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .mentionedUsers(mentioned)
                .version(comment.getVersion())
                .build();
    }
}
