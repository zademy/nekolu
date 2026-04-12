/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-12
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Standard error response following RFC 7807 Problem Details conventions.
 */
@Schema(description = "Standard API error response")
public record ApiErrorResponse(
    @Schema(description = "HTTP status code", example = "400")
    int status,

    @Schema(description = "Short error classification", example = "Bad Request")
    String error,

    @Schema(description = "Human-readable error message", example = "Folder name is required")
    String message,

    @Schema(description = "Request path that produced the error", example = "/api/telegram/files")
    String path,

    @Schema(description = "Timestamp of the error", example = "2026-04-12T22:00:00Z")
    Instant timestamp
) {
    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(status, error, message, path, Instant.now());
    }
}
