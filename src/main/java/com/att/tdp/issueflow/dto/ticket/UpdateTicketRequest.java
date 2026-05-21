package com.att.tdp.issueflow.dto.ticket;

import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTicketRequest {

    @Size(min = 1, message = "Ticket title must not be empty")
    private String title;
    private String description;
    private TicketStatus status;
    private TicketPriority priority;
    private Long assigneeId;
    private LocalDateTime dueDate;
}
