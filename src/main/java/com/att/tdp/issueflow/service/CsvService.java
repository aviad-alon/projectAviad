package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.ticket.CreateTicketRequest;
import com.att.tdp.issueflow.dto.ticket.CsvImportResult;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import com.att.tdp.issueflow.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CsvService {

    // CSV column headers - shared by export and import
    private static final String[] HEADERS = {"ID", "Title", "Description", "Status", "Priority", "Type", "AssigneeId"};

    private final TicketRepository ticketRepository;
    private final TicketService    ticketService;
    private final ProjectService   projectService;

    // ---------------------------------------------------------------
    // EXPORT  -  stream all active tickets for a project as CSV
    // ---------------------------------------------------------------
    public void exportTicketsToCsv(Long projectId, Writer writer) throws IOException {
        projectService.findActiveProjectOrThrow(projectId);   // 404 if project doesn't exist

        List<Ticket> tickets = ticketRepository.findByProjectIdAndDeletedAtIsNull(projectId);

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(HEADERS)
                .build();

        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (Ticket t : tickets) {
                Long assigneeId = (t.getAssignee() != null) ? t.getAssignee().getId() : null;

                printer.printRecord(
                        t.getId(),
                        t.getTitle(),
                        t.getDescription() != null ? t.getDescription() : "",
                        t.getStatus().name(),
                        t.getPriority().name(),
                        t.getType().name(),
                        assigneeId != null ? assigneeId : ""
                );
            }
        }
    }

    // ---------------------------------------------------------------
    // IMPORT  -  parse uploaded CSV and create tickets row by row
    // ---------------------------------------------------------------
    @Transactional
    public CsvImportResult importTicketsFromCsv(InputStream inputStream,
                                                Long projectId,
                                                User currentUser) throws IOException {
        projectService.findActiveProjectOrThrow(projectId);   // 404 if project doesn't exist

        int successfulCount = 0;
        int failedCount     = 0;
        List<String> errors = new java.util.ArrayList<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(HEADERS)
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, format)) {

            for (CSVRecord record : parser) {
                long rowNumber = record.getRecordNumber();
                try {
                    CreateTicketRequest request = parseRow(record, projectId);
                    ticketService.createTicket(request, currentUser);
                    successfulCount++;
                } catch (Exception e) {
                    failedCount++;
                    errors.add("Row " + rowNumber + ": " + e.getMessage());
                }
            }
        }

        return CsvImportResult.builder()
                .created(successfulCount)
                .failed(failedCount)
                .errors(errors)
                .build();
    }

    // ---------------------------------------------------------------
    // Parse a single CSV row into a CreateTicketRequest
    // ---------------------------------------------------------------
    private CreateTicketRequest parseRow(CSVRecord record, Long projectId) {
        String title = record.get("Title");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }

        // Priority - required
        String priorityRaw = record.get("Priority");
        if (priorityRaw == null || priorityRaw.isBlank()) {
            throw new IllegalArgumentException("Priority is required");
        }
        TicketPriority priority;
        try {
            priority = TicketPriority.valueOf(priorityRaw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid priority: " + priorityRaw);
        }

        // Type - required
        String typeRaw = record.get("Type");
        if (typeRaw == null || typeRaw.isBlank()) {
            throw new IllegalArgumentException("Type is required");
        }
        TicketType type;
        try {
            type = TicketType.valueOf(typeRaw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid type: " + typeRaw);
        }

        // Status - optional, default TODO
        TicketStatus status = TicketStatus.TODO;
        String statusRaw = record.get("Status");
        if (statusRaw != null && !statusRaw.isBlank()) {
            try {
                status = TicketStatus.valueOf(statusRaw.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status: " + statusRaw);
            }
        }

        // Description - optional
        String description = record.get("Description");

        // AssigneeId - optional; null triggers auto-assignment in TicketService
        Long assigneeId = null;
        String assigneeIdRaw = record.get("AssigneeId");
        if (assigneeIdRaw != null && !assigneeIdRaw.isBlank()) {
            try {
                assigneeId = Long.parseLong(assigneeIdRaw);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid assigneeId: " + assigneeIdRaw);
            }
        }

        return CreateTicketRequest.builder()
                .title(title)
                .description(description)
                .status(status)
                .priority(priority)
                .type(type)
                .projectId(projectId)
                .assigneeId(assigneeId)   // null → auto-assignment
                .build();
    }

}
