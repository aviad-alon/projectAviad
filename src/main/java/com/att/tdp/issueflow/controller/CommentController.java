package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.comment.CommentResponse;
import com.att.tdp.issueflow.dto.comment.CreateCommentRequest;
import com.att.tdp.issueflow.dto.comment.MentionsPageResponse;
import com.att.tdp.issueflow.dto.comment.UpdateCommentRequest;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.service.CommentService;
import com.att.tdp.issueflow.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Handles two URL namespaces:
 *   /api/tickets/{ticketId}/comments  - ticket-scoped comment CRUD
 *   /api/comments/mentions            - current-user mention feed
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final UserService    userService;

    // POST /api/tickets/{ticketId}/comments
    @PostMapping("/tickets/{ticketId}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.addComment(ticketId, request, currentUser));
    }

    // GET /api/tickets/{ticketId}/comments
    @GetMapping("/tickets/{ticketId}/comments")
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long ticketId) {
        return ResponseEntity.ok(commentService.getCommentsForTicket(ticketId));
    }

    // PATCH /api/tickets/{ticketId}/comments/{commentId}
    @PatchMapping("/tickets/{ticketId}/comments/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable Long ticketId,
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        return ResponseEntity.ok(commentService.updateComment(commentId, request, currentUser));
    }

    // DELETE /api/tickets/{ticketId}/comments/{commentId}
    @DeleteMapping("/tickets/{ticketId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long ticketId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        commentService.deleteComment(commentId, currentUser);
        return ResponseEntity.noContent().build();
    }

    // GET /api/comments/mentions  - paginated feed of comments mentioning the current user
    @GetMapping("/comments/mentions")
    public ResponseEntity<MentionsPageResponse> getMyMentions(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        return ResponseEntity.ok(
                userService.getUserMentions(currentUser.getId(), page, pageSize));
    }
}
