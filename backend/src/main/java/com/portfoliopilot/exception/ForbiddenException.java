package com.portfoliopilot.exception;

import org.springframework.http.HttpStatus;

/**
 * 403. Authenticated, but not permitted - e.g. a USER hitting an admin route.
 *
 * <p>Not used for "you do not own this document": that returns 404 so resource
 * ids cannot be probed. See {@link ResourceNotFoundException}.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
