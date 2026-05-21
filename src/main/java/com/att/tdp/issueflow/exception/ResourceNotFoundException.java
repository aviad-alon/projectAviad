package com.att.tdp.issueflow.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a requested entity does not exist - results in a 404 response. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {
    /** Creates the exception with a descriptive message identifying the missing resource. */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
