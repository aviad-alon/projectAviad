package com.att.tdp.issueflow.dto.ticket;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddDependencyRequest {

    @NotNull(message = "blockedBy ticket ID is required")
    private Long blockedBy;
}
