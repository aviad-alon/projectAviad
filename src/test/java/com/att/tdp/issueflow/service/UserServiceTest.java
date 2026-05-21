package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.user.CreateUserRequest;
import com.att.tdp.issueflow.dto.user.UpdateUserRequest;
import com.att.tdp.issueflow.dto.user.UserResponse;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.UserRole;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository    userRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private AuditLogService   auditLogService;
    @Mock private PasswordEncoder   passwordEncoder;

    @InjectMocks
    private UserService userService;

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------
    private CreateUserRequest createRequest(String username, String email, UserRole role) {
        return CreateUserRequest.builder()
                .username(username)
                .email(email)
                .fullName("Full Name")
                .role(role)
                .password("secret123")
                .build();
    }

    private User savedUser(Long id, String username, String email, UserRole role) {
        return User.builder()
                .id(id)
                .username(username)
                .email(email)
                .fullName("Full Name")
                .role(role)
                .password("hashed")
                .build();
    }

    // ==================================================================
    // createUser
    // ==================================================================

    @Test
    void createUser_success_returnsCorrectUserResponse() {
        CreateUserRequest req = createRequest("alice", "alice@example.com", UserRole.DEVELOPER);

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser(1L, "alice", "alice@example.com", UserRole.DEVELOPER));

        UserResponse response = userService.createUser(req);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getRole()).isEqualTo(UserRole.DEVELOPER);

        verify(passwordEncoder).encode("secret123");
        verify(userRepository).save(any(User.class));
        verify(auditLogService).log("CREATE", "USER", 1L, null);
    }

    @Test
    void createUser_duplicateUsername_throwsConflictException() {
        CreateUserRequest req = createRequest("alice", "alice@example.com", UserRole.DEVELOPER);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("alice");

        verify(userRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), anyString(), any(), any());
    }

    @Test
    void createUser_duplicateEmail_throwsConflictException() {
        CreateUserRequest req = createRequest("alice", "alice@example.com", UserRole.DEVELOPER);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("alice@example.com");

        verify(userRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), anyString(), any(), any());
    }

    @Test
    void createUser_passwordIsHashedBeforeSaving() {
        CreateUserRequest req = createRequest("bob", "bob@example.com", UserRole.ADMIN);

        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser(2L, "bob", "bob@example.com", UserRole.ADMIN));

        userService.createUser(req);

        // The plain-text password must never reach the repository
        verify(passwordEncoder).encode("secret123");
        verify(userRepository).save(argThat(u -> "$2a$10$hashedPassword".equals(u.getPassword())));
    }

    // ==================================================================
    // getAllUsers
    // ==================================================================

    @Test
    void getAllUsers_returnsAllUsersAsMappedResponses() {
        List<User> users = List.of(
                savedUser(1L, "alice", "alice@example.com", UserRole.DEVELOPER),
                savedUser(2L, "bob",   "bob@example.com",   UserRole.ADMIN)
        );
        when(userRepository.findAll()).thenReturn(users);

        List<UserResponse> result = userService.getAllUsers();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserResponse::getUsername)
                .containsExactly("alice", "bob");
    }

    // ==================================================================
    // getUserById
    // ==================================================================

    @Test
    void getUserById_existingUser_returnsUserResponse() {
        User user = savedUser(5L, "carol", "carol@example.com", UserRole.ADMIN);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserById(5L);

        assertThat(response.getId()).isEqualTo(5L);
        assertThat(response.getUsername()).isEqualTo("carol");
        assertThat(response.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void getUserById_nonExistentUser_throwsResourceNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ==================================================================
    // updateUser
    // ==================================================================

    @Test
    void updateUser_emailChange_updatesEmailAndLogsAudit() {
        User existing = savedUser(3L, "dave", "dave@old.com", UserRole.DEVELOPER);
        when(userRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("dave@new.com")).thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser(3L, "dave", "dave@new.com", UserRole.DEVELOPER));

        UpdateUserRequest req = UpdateUserRequest.builder().email("dave@new.com").build();

        UserResponse response = userService.updateUser(3L, req);

        assertThat(response.getEmail()).isEqualTo("dave@new.com");
        verify(userRepository).save(any(User.class));
        verify(auditLogService).log("UPDATE", "USER", 3L, null);
    }

    @Test
    void updateUser_duplicateNewEmail_throwsConflictException() {
        User existing = savedUser(3L, "dave", "dave@old.com", UserRole.DEVELOPER);
        when(userRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        UpdateUserRequest req = UpdateUserRequest.builder().email("taken@example.com").build();

        assertThatThrownBy(() -> userService.updateUser(3L, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("taken@example.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_sameEmail_doesNotCheckForDuplicate() {
        // If the new email equals the current email, the uniqueness check must be skipped
        User existing = savedUser(3L, "dave", "dave@same.com", UserRole.DEVELOPER);
        when(userRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenReturn(existing);

        UpdateUserRequest req = UpdateUserRequest.builder().email("dave@same.com").build();

        userService.updateUser(3L, req);

        verify(userRepository, never()).existsByEmail(anyString());
    }

    // ==================================================================
    // deleteUser
    // ==================================================================

    @Test
    void deleteUser_callsRepositoryDeleteAndLogsAudit() {
        User user = savedUser(7L, "eve", "eve@example.com", UserRole.DEVELOPER);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        userService.deleteUser(7L);

        verify(userRepository).delete(user);
        verify(auditLogService).log("DELETE", "USER", 7L, null);
    }

    @Test
    void deleteUser_nonExistentUser_throwsResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(999L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).delete(any());
    }
}
