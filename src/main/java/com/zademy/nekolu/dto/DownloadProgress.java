/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for download progress (SSE).
 */
@Schema(description = "File download progress")
public record DownloadProgress(
    @Schema(description = "File ID", example = "12345")
    long fileId,

    @Schema(description = "Download status", example = "DOWNLOADING", allowableValues = {"PENDING", "DOWNLOADING", "COMPLETED", "FAILED"})
    String status,

    @Schema(description = "Downloaded bytes", example = "5242880")
    long downloadedBytes,

    @Schema(description = "Total size in bytes", example = "10485760")
    long totalBytes,

    @Schema(description = "Progress percentage (0-100)", example = "50")
    int progressPercentage,

    @Schema(description = "Download speed in bytes/second", example = "1024000")
    long downloadSpeed,

    @Schema(description = "Estimated remaining time in seconds", example = "5", nullable = true)
    Integer estimatedTimeSeconds,

    @JsonIgnore
    @Schema(hidden = true)
    String localPath,

    @Schema(description = "Indicates whether the file can already be downloaded directly", example = "false")
    boolean downloadReady,

    @Schema(description = "Internal download URL", example = "/api/telegram/files/12345/content", nullable = true)
    String downloadUrl,

    @Schema(description = "Event timestamp", example = "1678886400000")
    long timestamp
) {
    public DownloadProgress(
        long fileId,
        String status,
        long downloadedBytes,
        long totalBytes,
        int progressPercentage,
        long downloadSpeed,
        Integer estimatedTimeSeconds,
        String localPath,
        long timestamp
    ) {
        this(
            fileId,
            status,
            downloadedBytes,
            totalBytes,
            progressPercentage,
            downloadSpeed,
            estimatedTimeSeconds,
            localPath,
            DownloadResponse.STATUS_COMPLETED.equals(status) && localPath != null && !localPath.isBlank(),
            DownloadResponse.STATUS_COMPLETED.equals(status) && localPath != null && !localPath.isBlank()
                ? "/api/telegram/files/" + fileId + "/content"
                : null,
            timestamp
        );
    }
}
