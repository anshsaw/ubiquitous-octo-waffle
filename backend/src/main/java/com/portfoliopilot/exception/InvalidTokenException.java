package com.portfoliopilot.exception;

import org.springframework.http.HttpStatus;

/** 401. Malformed, expired, revoked or reused JWT / refresh token. */
public class InvalidTokenException extends ApiException {

    public InvalidTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
