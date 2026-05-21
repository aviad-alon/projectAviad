package com.att.tdp.issueflow.dto.ticket;

import com.att.tdp.issueflow.entity.Attachment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachmentResponse {

    private Long   id;
    private Long   ticketId;
    private String filename;
    private String contentType;

    public static AttachmentResponse from(Attachment a) {
        return AttachmentResponse.builder()
                .id(a.getId())
                .ticketId(a.getTicket() != null ? a.getTicket().getId() : null)
                .filename(a.getFilename())
                .contentType(a.getContentType())
                .build();
    }
}
