package com.portfoliopilot.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for every deliberate, client-facing failure.
 *
 * <p>Carrying the {@link HttpStatus} on the exception keeps
 * {@code GlobalExceptionHandler} free of a growing if/else chain: each subclass
 * declares its own status once.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
