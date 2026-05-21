package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.comment.CommentResponse;
import com.att.tdp.issueflow.dto.comment.MentionsPageResponse;
import com.att.tdp.issueflow.dto.user.CreateUserRequest;
import com.att.tdp.issueflow.dto.user.UpdateUserRequest;
import com.att.tdp.issueflow.dto.user.UserResponse;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/** Service handling user registration, retrieval, updates, deletion, and mention queries. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository    userRepository;
    private final CommentRepository commentRepository;
    private final AuditLogService   auditLogService;
    private final PasswordEncoder   passwordEncoder;

    // ---------------------------------------------------------------
    // GET all users
    // ---------------------------------------------------------------
    /** Returns all users in the system as response DTOs. */
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // GET user by ID
    // ---------------------------------------------------------------
    /** Returns a single user by ID, throwing 404 if not found. */
    public UserResponse getUserById(Long id) {
        User user = findUserOrThrow(id);
        return UserResponse.from(user);
    }

    // ---------------------------------------------------------------
    // CREATE user (registration)
    // ---------------------------------------------------------------
    /**
     * Creates a new user after checking for duplicate username and email.
     * Password is BCrypt-hashed before storage.
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username already exists: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already in use: " + request.getEmail());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .role(request.getRole())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        User saved = userRepository.save(user);

        auditLogService.log("CREATE", "USER", saved.getId(), null);

        return UserResponse.from(saved);
    }

    // ---------------------------------------------------------------
    // UPDATE user
    // ---------------------------------------------------------------
    /** Applies non-null fields from the request to the existing user, checking for email uniqueness. */
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findUserOrThrow(id);

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ConflictException("Email already in use: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        User saved = userRepository.save(user);
        auditLogService.log("UPDATE", "USER", saved.getId(), null);

        return UserResponse.from(saved);
    }

    // ---------------------------------------------------------------
    // DELETE user
    // ---------------------------------------------------------------
    /** Permanently deletes the user with the given ID and logs the action. */
    @Transactional
    public void deleteUser(Long id) {
        User user = findUserOrThrow(id);
        auditLogService.log("DELETE", "USER", id, null);
        userRepository.delete(user);
    }

    // ---------------------------------------------------------------
    // GET mentions for a user  (GET /users/:id/mentions)
    // ---------------------------------------------------------------
    /** Returns a paginated list of comments that mention the specified user. */
    public MentionsPageResponse getUserMentions(Long userId, int page, int pageSize) {
        findUserOrThrow(userId);   // 404 if user doesn't exist

        Page<com.att.tdp.issueflow.entity.Comment> mentionsPage =
                commentRepository.findByMentionedUserId(
                        userId, PageRequest.of(page - 1, pageSize));

        List<CommentResponse> data = mentionsPage.getContent()
                .stream()
                .map(CommentResponse::from)
                .collect(Collectors.toList());

        return MentionsPageResponse.builder()
                .data(data)
                .page(page)
                .total(mentionsPage.getTotalElements())
                .totalPages(mentionsPage.getTotalPages())
                .build();
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------
    /** Looks up a user by ID and throws 404 if not found. */
    public User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    /** Looks up a user by username and throws 404 if not found. */
    public User findByUsernameOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
