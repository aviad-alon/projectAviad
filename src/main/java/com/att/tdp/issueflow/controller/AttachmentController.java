package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.ticket.AttachmentResponse;
import com.att.tdp.issueflow.entity.Attachment;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.service.AttachmentService;
import com.att.tdp.issueflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/tickets/{ticketId}/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final UserService       userService;

    // POST /api/tickets/{ticketId}/attachments  - multipart upload
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> upload(
            @PathVariable Long ticketId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails principal) throws IOException {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attachmentService.uploadAttachment(ticketId, file, currentUser));
    }

    // GET /api/tickets/{ticketId}/attachments
    @GetMapping
    public ResponseEntity<List<AttachmentResponse>> listAttachments(@PathVariable Long ticketId) {
        return ResponseEntity.ok(attachmentService.getAttachments(ticketId));
    }

    // GET /api/tickets/{ticketId}/attachments/{attachmentId}  - binary download
    @GetMapping("/{attachmentId}")
    public ResponseEntity<byte[]> download(
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId) {
        Attachment attachment = attachmentService.getAttachmentRaw(ticketId, attachmentId);

        String contentType = attachment.getContentType() != null
                ? attachment.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getFilename() + "\"")
                .body(attachment.getData());
    }

    // DELETE /api/tickets/{ticketId}/attachments/{attachmentId}
    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        attachmentService.deleteAttachment(ticketId, attachmentId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
