/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * File download status response.
 */
@Schema(description = "Download status of a file")
public record DownloadResponse(
    @Schema(description = "File ID", example = "12345")
    long fileId,
    @Schema(description = "Download status", example = "PENDING", allowableValues = {"PENDING", "DOWNLOADING", "COMPLETED", "FAILED"})
    String status,
    @JsonIgnore
    @Schema(hidden = true)
    String localPath,
    @Schema(description = "Progress (0-100)", example = "25", minimum = "0", maximum = "100")
    int progress,
    @Schema(description = "Indicates whether the file can be downloaded directly", example = "true")
    boolean downloadReady,
    @Schema(description = "Internal download URL", example = "/api/telegram/files/12345/content", nullable = true)
    String downloadUrl,
    @Schema(description = "Additional message (error or info)", example = "Download started", nullable = true)
    String message
) {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DOWNLOADING = "DOWNLOADING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    public DownloadResponse(long fileId, String status, String localPath, int progress, String message) {
        this(
            fileId,
            status,
            localPath,
            progress,
            STATUS_COMPLETED.equals(status) && localPath != null && !localPath.isBlank(),
            STATUS_COMPLETED.equals(status) && localPath != null && !localPath.isBlank()
                ? "/api/telegram/files/" + fileId + "/content"
                : null,
            message
        );
    }
}
