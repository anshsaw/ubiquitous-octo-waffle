package com.portfoliopilot.exception;

import com.portfoliopilot.dto.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns every exception into the standard {@link ApiResponse} envelope with a
 * correct HTTP status.
 *
 * <p>Two rules are enforced here:
 * <ol>
 *   <li>an unexpected exception NEVER leaks its message or stack trace to the
 *       client - it is logged server-side and the caller gets a generic 500;</li>
 *   <li>every deliberate failure carries a message the UI can display verbatim.</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** All deliberate, client-facing failures. Status comes from the exception. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex, HttpServletRequest request) {
        log.debug("API exception on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());

        Map<String, String> errors = (ex instanceof BusinessValidationException bve && !bve.getErrors().isEmpty())
                ? bve.getErrors()
                : null;

        return ResponseEntity.status(ex.getStatus())
                .body(errors == null
                        ? ApiResponse.error(ex.getMessage())
                        : ApiResponse.error(ex.getMessage(), errors));
    }

    /** {@code @Valid} failure on a request body. Returns a field -> message map. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errors.putIfAbsent(e.getObjectName(), e.getDefaultMessage()));

        return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", errors));
    }

    /** {@code @Validated} failure on a path variable or request parameter. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", errors));
    }

    /** Malformed JSON body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error("Malformed JSON request body"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Missing required parameter: " + ex.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Invalid value for parameter: " + ex.getName()));
    }

    /**
     * A unique index rejected the write.
     *
     * <p>This is the safety net behind the friendly pre-checks in the services:
     * two concurrent registrations can both pass "does this email exist?" and
     * only the index stops the duplicate.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("Unique index violation: {}", ex.getMessage());
        String message = describeDuplicate(ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(message));
    }

    /** Spring Security denied the request (missing role). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You do not have permission to perform this action"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication required"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("HTTP method not supported for this endpoint"));
    }

    /** Unknown /api path. Non-API paths belong to the frontend and are never routed here. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Endpoint not found"));
    }

    /**
     * Anything unplanned. The real cause is logged with a stack trace; the client
     * gets a generic message so internal details are never disclosed.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        // A MongoDB $jsonSchema rejection (error 121) surfaces here. It means a
        // document violated the collection validator - i.e. an application bug,
        // not bad user input. Surfacing it as 500 is correct.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }

    /** Best-effort friendly text for a duplicate-key message, without echoing the raw driver error. */
    private String describeDuplicate(String raw) {
        if (raw == null) {
            return "Resource already exists";
        }
        if (raw.contains("uniq_email")) {
            return "An account with this email already exists";
        }
        if (raw.contains("uniq_username")) {
            return "This username is already taken";
        }
        if (raw.contains("uniq_published_username")) {
            return "You already have a published portfolio. Unpublish it first.";
        }
        if (raw.contains("uniq_templateKey")) {
            return "A template with this key already exists";
        }
        return "Resource already exists";
    }
}
