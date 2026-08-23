package com.portfoliopilot.exception;

import org.springframework.http.HttpStatus;

/**
 * 404. Also thrown when a resource exists but is not owned by the caller.
 *
 * <p>That is deliberate: replying 403 to "someone else's project id" confirms
 * the id exists, which leaks information. 404 for both cases means an attacker
 * learns nothing by probing ids.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public static ResourceNotFoundException of(String resource) {
        return new ResourceNotFoundException(resource + " not found");
    }
}
