package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.comment.CreateCommentRequest;
import com.att.tdp.issueflow.dto.comment.UpdateCommentRequest;
import com.att.tdp.issueflow.entity.Comment;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import com.att.tdp.issueflow.enums.UserRole;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private UserRepository    userRepository;
    @Mock private TicketService     ticketService;
    @Mock private AuditLogService   auditLogService;

    @InjectMocks
    private CommentService commentService;

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------
    private User user(long id, String username) {
        return User.builder().id(id).username(username)
                .email(username + "@example.com").fullName(username)
                .role(UserRole.DEVELOPER).password("pw").build();
    }

    private Ticket ticket(long id) {
        return Ticket.builder().id(id).title("Ticket " + id)
                .status(TicketStatus.TODO).priority(TicketPriority.LOW)
                .type(TicketType.TECHNICAL).build();
    }

    private Comment savedComment(long id, Ticket ticket, User author, String content, List<User> mentions) {
        return Comment.builder()
                .id(id).ticket(ticket).author(author)
                .content(content).mentionedUsers(mentions)
                .createdAt(LocalDateTime.now()).build();
    }

    // ==================================================================
    // addComment - @mention parsing
    // ==================================================================

    @Test
    void addComment_twoKnownMentions_bothAttachedToSavedComment() {
        User alice  = user(1L, "alice");
        User bob    = user(2L, "bob");
        User author = user(3L, "carol");
        Ticket t    = ticket(10L);

        when(ticketService.findActiveTicketOrThrow(10L)).thenReturn(t);
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsernameIgnoreCase("bob")).thenReturn(Optional.of(bob));
        when(commentRepository.save(any(Comment.class)))
                .thenReturn(savedComment(100L, t, author,
                        "Hey @alice and @bob, please review.", List.of(alice, bob)));

        CreateCommentRequest req = CreateCommentRequest.builder()
                .content("Hey @alice and @bob, please review.")
                .build();

        commentService.addComment(10L, req, author);

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());

        List<User> mentioned = captor.getValue().getMentionedUsers();
        assertThat(mentioned).hasSize(2);
        assertThat(mentioned).containsExactlyInAnyOrder(alice, bob);
    }

    @Test
    void addComment_unknownMention_isSkippedSilently() {
        User author = user(1L, "alice");
        Ticket t    = ticket(5L);

        when(ticketService.findActiveTicketOrThrow(5L)).thenReturn(t);
        when(userRepository.findByUsernameIgnoreCase("nobody")).thenReturn(Optional.empty());
        when(commentRepository.save(any(Comment.class)))
                .thenReturn(savedComment(42L, t, author, "Hey @nobody!", List.of()));

        commentService.addComment(5L,
                CreateCommentRequest.builder().content("Hey @nobody!").build(),
                author);

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getMentionedUsers()).isEmpty();
    }

    @Test
    void addComment_noMentionSymbol_userRepositoryNeverQueried() {
        User author = user(1L, "alice");
        Ticket t    = ticket(7L);

        when(ticketService.findActiveTicketOrThrow(7L)).thenReturn(t);
        when(commentRepository.save(any(Comment.class)))
                .thenReturn(savedComment(55L, t, author, "Plain comment.", List.of()));

        commentService.addComment(7L,
                CreateCommentRequest.builder().content("Plain comment.").build(),
                author);

        // No @ in content → userRepository must not be touched at all
        verifyNoInteractions(userRepository);
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getMentionedUsers()).isEmpty();
    }

    @Test
    void addComment_threeMentions_allResolvedAndAttached() {
        User alice  = user(1L, "alice");
        User bob    = user(2L, "bob");
        User carol  = user(3L, "carol");
        User author = user(4L, "dave");
        Ticket t    = ticket(20L);

        when(ticketService.findActiveTicketOrThrow(20L)).thenReturn(t);
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsernameIgnoreCase("bob")).thenReturn(Optional.of(bob));
        when(userRepository.findByUsernameIgnoreCase("carol")).thenReturn(Optional.of(carol));
        when(commentRepository.save(any(Comment.class)))
                .thenReturn(savedComment(200L, t, author,
                        "@alice @bob @carol LGTM!", List.of(alice, bob, carol)));

        commentService.addComment(20L,
                CreateCommentRequest.builder().content("@alice @bob @carol LGTM!").build(),
                author);

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getMentionedUsers())
                .containsExactlyInAnyOrder(alice, bob, carol);
    }

    @Test
    void addComment_mixedKnownAndUnknownMentions_onlyKnownUsersAttached() {
        User alice  = user(1L, "alice");
        User author = user(2L, "bob");
        Ticket t    = ticket(30L);

        when(ticketService.findActiveTicketOrThrow(30L)).thenReturn(t);
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());
        when(commentRepository.save(any(Comment.class)))
                .thenReturn(savedComment(300L, t, author,
                        "cc @alice and @ghost", List.of(alice)));

        commentService.addComment(30L,
                CreateCommentRequest.builder().content("cc @alice and @ghost").build(),
                author);

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getMentionedUsers())
                .containsExactly(alice);
    }

    @Test
    void addComment_ticketNotFound_throwsResourceNotFoundException() {
        when(ticketService.findActiveTicketOrThrow(999L))
                .thenThrow(new ResourceNotFoundException("Ticket not found: 999"));

        assertThatThrownBy(() -> commentService.addComment(999L,
                CreateCommentRequest.builder().content("test").build(),
                user(1L, "alice")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");

        verify(commentRepository, never()).save(any());
    }

    @Test
    void addComment_logsAuditWithCorrectAction() {
        User author = user(1L, "alice");
        Ticket t    = ticket(10L);

        when(ticketService.findActiveTicketOrThrow(10L)).thenReturn(t);
        Comment saved = savedComment(77L, t, author, "Hello", List.of());
        when(commentRepository.save(any(Comment.class))).thenReturn(saved);

        commentService.addComment(10L,
                CreateCommentRequest.builder().content("Hello").build(),
                author);

        verify(auditLogService).log("ADD_COMMENT", "COMMENT", 77L, author);
    }

    // ==================================================================
    // updateComment
    // ==================================================================

    @Test
    void updateComment_updatesContentAndLogsAudit() {
        User author = user(1L, "alice");
        Ticket t    = ticket(10L);
        Comment existing = savedComment(50L, t, author, "Old content", List.of());

        when(ticketService.findActiveTicketOrThrow(10L)).thenReturn(t);
        when(commentRepository.findById(50L)).thenReturn(Optional.of(existing));
        Comment updated = savedComment(50L, t, author, "New content", List.of());
        when(commentRepository.save(any(Comment.class))).thenReturn(updated);

        commentService.updateComment(10L, 50L,
                UpdateCommentRequest.builder().content("New content").build(),
                author);

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("New content");

        verify(auditLogService).log("UPDATE", "COMMENT", 50L, author);
    }

    @Test
    void updateComment_commentNotFound_throwsResourceNotFoundException() {
        when(commentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.updateComment(10L, 404L,
                UpdateCommentRequest.builder().content("anything").build(),
                user(1L, "alice")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");

        verify(commentRepository, never()).save(any());
    }

    @Test
    void updateComment_wrongTicketId_throwsResourceNotFoundException() {
        User author  = user(1L, "alice");
        Ticket t     = ticket(10L);   // comment belongs to ticket 10
        Comment existing = savedComment(50L, t, author, "Content", List.of());

        when(commentRepository.findById(50L)).thenReturn(Optional.of(existing));

        // ticketId=20 but comment belongs to ticket 10 → should throw
        assertThatThrownBy(() -> commentService.updateComment(20L, 50L,
                UpdateCommentRequest.builder().content("Updated").build(),
                author))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("50");

        verify(commentRepository, never()).save(any());
    }

    // ==================================================================
    // deleteComment
    // ==================================================================

    @Test
    void deleteComment_existingComment_deletesAndLogsAudit() {
        User author  = user(1L, "alice");
        Ticket t     = ticket(10L);
        Comment existing = savedComment(50L, t, author, "Some comment", List.of());

        when(ticketService.findActiveTicketOrThrow(10L)).thenReturn(t);
        when(commentRepository.findById(50L)).thenReturn(Optional.of(existing));

        commentService.deleteComment(10L, 50L, author);

        verify(commentRepository).delete(existing);
        verify(auditLogService).log("DELETE", "COMMENT", 50L, author);
    }

    @Test
    void deleteComment_commentNotFound_throwsResourceNotFoundException() {
        when(commentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.deleteComment(10L, 999L, user(1L, "alice")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");

        verify(commentRepository, never()).delete(any());
    }

    @Test
    void deleteComment_wrongTicketId_throwsResourceNotFoundException() {
        User author  = user(1L, "alice");
        Ticket t     = ticket(10L);   // comment belongs to ticket 10
        Comment existing = savedComment(50L, t, author, "Content", List.of());

        when(commentRepository.findById(50L)).thenReturn(Optional.of(existing));

        // ticketId=20 but comment belongs to ticket 10 → should throw
        assertThatThrownBy(() -> commentService.deleteComment(20L, 50L, author))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("50");

        verify(commentRepository, never()).delete(any());
    }
}
