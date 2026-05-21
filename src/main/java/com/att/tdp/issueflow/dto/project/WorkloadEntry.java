package com.att.tdp.issueflow.dto.project;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkloadEntry {

    private Long userId;
    private String username;
    private long openTicketCount;
}
