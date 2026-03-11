package com.atelier.api.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * A failure with a message that is safe to show to the user.
 * {@code field} names the input it relates to (or null), so forms can highlight it.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String field;
    /** Optional machine-readable reason (e.g. EMAIL_NOT_VERIFIED) for the client to act on. */
    private final String code;

    public BusinessException(String message, HttpStatus status, String field) {
        this(message, status, field, null);
    }

    public BusinessException(String message, HttpStatus status, String field, String code) {
        super(message);
        this.status = status;
        this.field = field;
        this.code = code;
    }

    public static BusinessException withCode(String message, HttpStatus status, String code) {
        return new BusinessException(message, status, null, code);
    }

    public BusinessException(String message, HttpStatus status) {
        this(message, status, null);
    }

    public static BusinessException notFound(String what) {
        return new BusinessException(what + " not found", HttpStatus.NOT_FOUND);
    }

    public static BusinessException conflict(String message, String field) {
        return new BusinessException(message, HttpStatus.CONFLICT, field);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(message, HttpStatus.FORBIDDEN);
    }

    public static BusinessException badRequest(String message, String field) {
        return new BusinessException(message, HttpStatus.BAD_REQUEST, field);
    }
}
