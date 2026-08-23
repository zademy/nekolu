/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-12
 */

package com.zademy.nekolu.controller;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.zademy.nekolu.dto.ApiErrorResponse;
import com.zademy.nekolu.exception.TelegramNotInitializedException;
import com.zademy.nekolu.exception.TelegramNotFoundException;
import com.zademy.nekolu.exception.TelegramOperationException;
import com.zademy.nekolu.exception.TelegramUnauthorizedException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Centralizes exception handling for all REST controllers.
 * Produces consistent {@link ApiErrorResponse} bodies for every error scenario.
 */
@RestControllerAdvice(basePackages = "com.zademy.nekolu.controller")
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        logger.warn("Validation error on {}: {}", request.getRequestURI(), details);
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of(400, "Validation Error", details, request.getRequestURI()));
    }

    @ExceptionHandler(TelegramUnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(TelegramUnauthorizedException ex,
                                                               HttpServletRequest request) {
        logger.warn("Unauthorized on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiErrorResponse.of(401, "Unauthorized", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(TelegramNotInitializedException.class)
    public ResponseEntity<ApiErrorResponse> handleNotInitialized(TelegramNotInitializedException ex,
                                                                 HttpServletRequest request) {
        logger.warn("Telegram module not initialized on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ApiErrorResponse.of(503, "Service Unavailable", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(TelegramOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleTelegramOperation(TelegramOperationException ex,
                                                                    HttpServletRequest request) {
        logger.warn("Telegram operation failed on {} [{}]: {}", request.getRequestURI(), ex.code(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                ApiErrorResponse.of(502, "Bad Gateway", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(TelegramNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTelegramNotFound(TelegramNotFoundException ex,
                                                                  HttpServletRequest request) {
        logger.warn("Telegram resource not found on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiErrorResponse.of(404, "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException ex,
                                                                HttpServletRequest request) {
        logger.warn("Illegal state on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of(400, "Bad Request", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex,
                                                                   HttpServletRequest request) {
        logger.warn("Bad argument on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of(400, "Bad Request", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleTimeout(TimeoutException ex,
                                                           HttpServletRequest request) {
        logger.error("Timeout on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(
                ApiErrorResponse.of(504, "Gateway Timeout",
                        "The operation timed out. Please try again.", request.getRequestURI()));
    }

    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<ApiErrorResponse> handleCompletionException(CompletionException ex,
                                                                       HttpServletRequest request) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        // Single typed dispatch: a new exception type registers in one line.
        return switch (cause) {
            case TelegramUnauthorizedException e -> handleUnauthorized(e, request);
            case TelegramNotInitializedException e -> handleNotInitialized(e, request);
            case TelegramOperationException e -> handleTelegramOperation(e, request);
            case TelegramNotFoundException e -> handleTelegramNotFound(e, request);
            case TimeoutException e -> handleTimeout(e, request);
            case IllegalStateException e -> handleIllegalState(e, request);
            default -> {
                logger.error("Async operation failed on {}: {}", request.getRequestURI(), cause.getMessage(), cause);
                yield ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        ApiErrorResponse.of(500, "Internal Server Error", cause.getMessage(), request.getRequestURI()));
            }
        };
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex,
                                                                 HttpServletRequest request) {
        logger.warn("Upload too large on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(
                ApiErrorResponse.of(413, "Payload Too Large",
                        "File exceeds the maximum upload size.", request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoResourceFoundException ex,
                                                            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiErrorResponse.of(404, "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex,
                                                           HttpServletRequest request) {
        logger.error("Unhandled exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiErrorResponse.of(500, "Internal Server Error",
                        "An unexpected error occurred.", request.getRequestURI()));
    }
}
