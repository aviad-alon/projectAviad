package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.ticket.AttachmentResponse;
import com.att.tdp.issueflow.entity.Attachment;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "application/pdf", "text/plain"
    );

    private final AttachmentRepository attachmentRepository;
    private final TicketService        ticketService;
    private final AuditLogService      auditLogService;

    // ---------------------------------------------------------------
    // GET all attachments for a ticket
    // ---------------------------------------------------------------
    public List<AttachmentResponse> getAttachments(Long ticketId) {
        ticketService.findActiveTicketOrThrow(ticketId);   // 404 if ticket doesn't exist

        return attachmentRepository.findByTicketId(ticketId)
                .stream()
                .map(AttachmentResponse::from)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // UPLOAD attachment (store binary in DB)
    // ---------------------------------------------------------------
    @Transactional
    public AttachmentResponse uploadAttachment(Long ticketId, MultipartFile file, User currentUser) {
        // Validate content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + contentType +
                    ". Allowed types: image/png, image/jpeg, application/pdf, text/plain");
        }

        Ticket ticket = ticketService.findActiveTicketOrThrow(ticketId);

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file: " + e.getMessage(), e);
        }

        Attachment attachment = Attachment.builder()
                .ticket(ticket)
                .filename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .data(data)
                .build();

        Attachment saved = attachmentRepository.save(attachment);
        auditLogService.log("UPLOAD_ATTACHMENT", "ATTACHMENT", saved.getId(), currentUser);

        return AttachmentResponse.from(saved);
    }

    // ---------------------------------------------------------------
    // DOWNLOAD attachment (return raw bytes)
    // ---------------------------------------------------------------
    public Attachment getAttachmentRaw(Long ticketId, Long attachmentId) {
        ticketService.findActiveTicketOrThrow(ticketId);

        return attachmentRepository.findById(attachmentId)
                .filter(a -> a.getTicket().getId().equals(ticketId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attachment not found: " + attachmentId));
    }

    // ---------------------------------------------------------------
    // DELETE attachment
    // ---------------------------------------------------------------
    @Transactional
    public void deleteAttachment(Long ticketId, Long attachmentId, User currentUser) {
        ticketService.findActiveTicketOrThrow(ticketId);

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .filter(a -> a.getTicket().getId().equals(ticketId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attachment not found: " + attachmentId));

        attachmentRepository.delete(attachment);
        auditLogService.log("DELETE", "ATTACHMENT", attachmentId, currentUser);
    }
}
