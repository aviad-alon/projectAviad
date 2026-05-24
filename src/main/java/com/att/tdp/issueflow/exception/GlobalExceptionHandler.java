package com.att.tdp.issueflow.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** Centralised exception handler - translates domain exceptions into consistent JSON error envelopes. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ---------------------------------------------------------------
    // Unified error envelope returned for every error response
    // ---------------------------------------------------------------
    /** Unified JSON error envelope returned for every error response. */
    @Data
    @Builder
    public static class ErrorResponse {
        private LocalDateTime timestamp;
        private int           status;
        private String        error;
        private String        message;
        private String        path;
        private List<String>  details;
    }

    // ---------------------------------------------------------------
    // 404 - resource not found
    // ---------------------------------------------------------------
    /** Handles ResourceNotFoundException and returns a 404 Not Found response. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), null, req);
    }

    // ---------------------------------------------------------------
    // 409 - duplicate / conflict
    // ---------------------------------------------------------------
    /** Handles ConflictException and returns a 409 Conflict response. */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), null, req);
    }

    // ---------------------------------------------------------------
    // 409 - optimistic locking conflict (concurrent update detected)
    // ---------------------------------------------------------------
    /** Handles optimistic locking failures and returns a 409 Conflict response. */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(
            org.springframework.orm.ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Conflict",
                "Ticket was modified by another user - please reload and try again", null, req);
    }

    // ---------------------------------------------------------------
    // 400 - bad argument (business rule violations, bad CSV row, etc.)
    // ---------------------------------------------------------------
    /** Handles IllegalArgumentException and returns a 400 Bad Request response. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), null, req);
    }

    // ---------------------------------------------------------------
    // 400 - illegal state (e.g. restoring a non-deleted resource)
    // ---------------------------------------------------------------
    /** Handles IllegalStateException and returns a 400 Bad Request response. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), null, req);
    }

    // ---------------------------------------------------------------
    // 401 - invalid credentials (wrong username or password)
    // ---------------------------------------------------------------
    /** Handles BadCredentialsException and returns a 401 Unauthorized response. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid username or password", null, req);
    }

    // ---------------------------------------------------------------
    // 403 - Spring Security access denied
    // ---------------------------------------------------------------
    /** Handles AccessDeniedException and returns a 403 Forbidden response. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Forbidden", "Access denied", null, req);
    }

    // ---------------------------------------------------------------
    // 400 - JSR-380 bean validation failures (@Valid)
    // ---------------------------------------------------------------
    /** Handles bean validation failures and returns a 400 response listing each field error. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> details = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());

        return build(HttpStatus.BAD_REQUEST, "Validation Failed",
                "Request validation failed", details, req);
    }

    // ---------------------------------------------------------------
    // 413 - file upload exceeds configured maximum size
    // ---------------------------------------------------------------
    /** Handles oversized file uploads and returns a 413 Payload Too Large response. */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleFileTooLarge(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large",
                "File size exceeds the maximum allowed limit of 10 MB", null, req);
    }

    // ---------------------------------------------------------------
    // 500 - catch-all for unexpected errors
    // ---------------------------------------------------------------
    /** Catch-all handler for unrecognised exceptions - returns a 500 Internal Server Error response. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred", null, req);
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------
    /** Builds the standard ErrorResponse envelope and wraps it in a ResponseEntity. */
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error,
                                                 String message, List<String> details,
                                                 HttpServletRequest req) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(req.getRequestURI())
                .details(details != null ? details : List.of())
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
