package com.portfoliopilot.exception;

import org.springframework.http.HttpStatus;

/** 409. Email/username already taken, template key already used, etc. */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
