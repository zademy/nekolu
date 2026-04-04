/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response payload that summarizes the outcome of a bulk delete operation.
 */
@Schema(description = "Batch deletion response for messages/files")
public record BulkDeleteResponse(
    @Schema(description = "Total files requested for deletion", example = "10")
    int totalRequested,

    @Schema(description = "Number of files deleted successfully", example = "8")
    int successCount,

    @Schema(description = "Number of files that failed to delete", example = "2")
    int failedCount,

    @Schema(description = "Individual results for each deletion")
    List<DeleteResult> results
) {
    @Schema(description = "Individual result of a file deletion")
    public record DeleteResult(
        @Schema(description = "File ID", example = "12345")
        int fileId,

        @Schema(description = "File name", example = "photo_12345.jpg")
        String fileName,

        @Schema(description = "Indicates whether the deletion was successful", example = "true")
        boolean success,

        @Schema(description = "Result or error message", example = "Message deleted successfully")
        String message
    ) {}
}
