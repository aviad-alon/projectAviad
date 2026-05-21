package com.att.tdp.issueflow.dto.comment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentionsPageResponse {

    private List<CommentResponse> data;
    private int page;
    private long total;
    private int totalPages;
}
