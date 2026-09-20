package com.uteexpress.common.exception;

import com.uteexpress.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<Object> handleApplication(ApplicationException exception, HttpServletRequest request) {
        ErrorCode code = exception.errorCode();
        return response(code.status(), code.name(), code.message(), request.getRequestURI(), List.of(), new HttpHeaders());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        ErrorCode code = ErrorCode.ACCESS_DENIED;
        return response(code.status(), code.name(), code.message(), request.getRequestURI(), List.of(), new HttpHeaders());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorResponse.Violation> errors = exception.getBindingResult().getAllErrors().stream()
                .map(error -> new ErrorResponse.Violation(
                        error instanceof org.springframework.validation.FieldError fieldError
                                ? fieldError.getField() : error.getObjectName(),
                        "Invalid value."))
                .toList();
        return response(status, ErrorCode.VALIDATION_FAILED.name(), ErrorCode.VALIDATION_FAILED.message(),
                path(request), errors, headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Preserve framework statuses/headers (e.g. 405 Allow), never expose exception messages.
        String code = status.value() == 404 ? ErrorCode.RESOURCE_NOT_FOUND.name()
                : status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR.name() : ErrorCode.INVALID_REQUEST.name();
        String message = status.value() == 404 ? ErrorCode.RESOURCE_NOT_FOUND.message()
                : status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR.message() : ErrorCode.INVALID_REQUEST.message();
        return response(status, code, message, path(request), List.of(), headers);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, HttpServletRequest request) {
        // Do not log payloads or exception messages that may contain credentials or database details.
        LOG.error("Unhandled request failure of type {}", exception.getClass().getName());
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return response(code.status(), code.name(), code.message(), request.getRequestURI(), List.of(), new HttpHeaders());
    }

    private static String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }

    private static ResponseEntity<Object> response(HttpStatusCode status, String code, String message,
            String path, List<ErrorResponse.Violation> errors, HttpHeaders headers) {
        return new ResponseEntity<>(new ErrorResponse(Instant.now(), status.value(), code, message, path, errors),
                headers, status);
    }
}
