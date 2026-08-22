package com.portfoliopilot.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 400. A rule that Bean Validation cannot express, because it spans fields or
 * documents - e.g. {@code endYear < startYear}, or selecting a portfolio section
 * the chosen template does not support.
 */
public class BusinessValidationException extends ApiException {

    private final Map<String, String> errors;

    public BusinessValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
        this.errors = Map.of();
    }

    public BusinessValidationException(String message, Map<String, String> errors) {
        super(HttpStatus.BAD_REQUEST, message);
        this.errors = errors == null ? Map.of() : errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
