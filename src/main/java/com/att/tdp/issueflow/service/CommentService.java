package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.comment.CommentResponse;
import com.att.tdp.issueflow.dto.comment.CreateCommentRequest;
import com.att.tdp.issueflow.dto.comment.UpdateCommentRequest;
import com.att.tdp.issueflow.entity.Comment;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");

    private final CommentRepository commentRepository;
    private final UserRepository    userRepository;
    private final TicketService     ticketService;
    private final AuditLogService   auditLogService;

    // ---------------------------------------------------------------
    // GET all comments for a ticket (ordered by createdAt ASC)
    // ---------------------------------------------------------------
    public List<CommentResponse> getCommentsForTicket(Long ticketId) {
        ticketService.findActiveTicketOrThrow(ticketId);   // 404 if ticket doesn't exist

        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
                .stream()
                .map(CommentResponse::from)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // ADD a comment (with @mention parsing)
    // ---------------------------------------------------------------
    @Transactional
    public CommentResponse addComment(Long ticketId, CreateCommentRequest request, User author) {
        Ticket ticket = ticketService.findActiveTicketOrThrow(ticketId);

        List<User> mentionedUsers = parseMentions(request.getContent());

        Comment comment = Comment.builder()
                .ticket(ticket)
                .author(author)
                .content(request.getContent())
                .mentionedUsers(mentionedUsers)
                .build();

        Comment saved = commentRepository.save(comment);
        auditLogService.log("ADD_COMMENT", "COMMENT", saved.getId(), author);

        return CommentResponse.from(saved);
    }

    // ---------------------------------------------------------------
    // UPDATE a comment's content
    // ---------------------------------------------------------------
    @Transactional
    public CommentResponse updateComment(Long commentId, UpdateCommentRequest request, User currentUser) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        comment.setContent(request.getContent());
        comment.setMentionedUsers(parseMentions(request.getContent()));
        Comment saved = commentRepository.save(comment);
        auditLogService.log("UPDATE", "COMMENT", saved.getId(), currentUser);

        return CommentResponse.from(saved);
    }

    // ---------------------------------------------------------------
    // DELETE a comment
    // ---------------------------------------------------------------
    @Transactional
    public void deleteComment(Long commentId, User currentUser) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        commentRepository.delete(comment);
        auditLogService.log("DELETE", "COMMENT", commentId, currentUser);
    }

    // ---------------------------------------------------------------
    // Parse @username mentions from comment text
    // ---------------------------------------------------------------
    private List<User> parseMentions(String content) {
        if (content == null || content.isBlank()) {
            return new ArrayList<>();
        }

        LinkedHashSet<User> mentioned = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String username = matcher.group(1);   // captured group: the word after @
            userRepository.findByUsernameIgnoreCase(username).ifPresent(mentioned::add);
        }

        return new ArrayList<>(mentioned);
    }
}
