package com.portfoliopilot.exception;

import org.springframework.http.HttpStatus;

/** 401. Not authenticated, bad credentials, or the account cannot sign in. */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
