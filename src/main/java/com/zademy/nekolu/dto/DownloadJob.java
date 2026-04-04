/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * DTO for batch download jobs.
 */
@Schema(description = "Batch file download job")
public record DownloadJob(
    @Schema(description = "Unique job ID", example = "abc123-def456")
    String jobId,

    @Schema(description = "Job status", example = "IN_PROGRESS", allowableValues = {"PENDING", "IN_PROGRESS", "COMPLETED", "FAILED", "CANCELLED"})
    String status,

    @Schema(description = "File IDs to download", example = "[12345, 12346, 12347]")
    List<Long> fileIds,

    @Schema(description = "Number of completed files", example = "25")
    int completedCount,

    @Schema(description = "Total number of files", example = "100")
    int totalCount,

    @Schema(description = "Progress percentage (0-100)", example = "25")
    int progressPercentage,

    @Schema(description = "Creation timestamp", example = "2026-03-29T10:00:00Z")
    Instant createdAt,

    @Schema(description = "Completion timestamp", example = "2026-03-29T10:05:00Z", nullable = true)
    Instant completedAt,

    @Schema(description = "Error message if it failed", example = "Connection timeout", nullable = true)
    String errorMessage
) {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";
}
