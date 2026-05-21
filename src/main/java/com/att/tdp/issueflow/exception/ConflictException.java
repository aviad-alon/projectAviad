package com.att.tdp.issueflow.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when an operation would create a duplicate or violate a uniqueness constraint - results in a 409 response. */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {
    /** Creates the exception with a descriptive message explaining the conflict. */
    public ConflictException(String message) {
        super(message);
    }
}
