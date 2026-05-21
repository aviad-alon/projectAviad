package com.att.tdp.issueflow.dto.ticket;

import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTicketRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @Builder.Default
    private TicketStatus status = TicketStatus.TODO;

    @NotNull(message = "Priority is required")
    private TicketPriority priority;

    @NotNull(message = "Type is required")
    private TicketType type;

    @NotNull(message = "Project ID is required")
    private Long projectId;

    /** If null, auto-assignment will apply */
    private Long assigneeId;

    private LocalDateTime dueDate;
}
