package com.transport.tracker.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
* Centralised exception handler. All API errors go through here,
* ensuring consistent error response shape and HTTP status codes.
*/
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── Validation errors ────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(fe -> ErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .rejectedValue(fe.getRejectedValue() != null ? fe.getRejectedValue().toString() : null)
                        .message(fe.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());

        ErrorResponse body = ErrorResponse.builder()
                .status(400)
                .error("Bad Request")
                .message("Validation failed")
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    // ─── Missing required params ──────────────────────────────────────────────

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        ErrorResponse body = ErrorResponse.builder()
                .status(400)
                .error("Bad Request")
                .message("Required parameter '" + ex.getParameterName() + "' is missing")
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    // ─── Transit API errors (returns 503 with context) ────────────────────────

    @ExceptionHandler(TransitApiException.class)
    public ResponseEntity<ErrorResponse> handleTransitApi(
            TransitApiException ex, HttpServletRequest request) {

        log.error("TransitApiException at {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .status(503)
                .error("Service Unavailable")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    // ─── Catch-all ────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ErrorResponse body = ErrorResponse.builder()
                .status(500)
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please try again.")
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.internalServerError().body(body);
    }
}