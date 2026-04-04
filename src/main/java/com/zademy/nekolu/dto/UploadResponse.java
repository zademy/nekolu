/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * File upload status response for Telegram.
 */
@Schema(description = "Telegram file upload status")
public record UploadResponse(
    @Schema(description = "Created message ID (when applicable)", example = "123456789")
    long messageId,
    @Schema(description = "Target chat ID", example = "261468790")
    long chatId,
    @Schema(description = "Upload status", example = "COMPLETED", allowableValues = {"PENDING", "UPLOADING", "COMPLETED", "FAILED"})
    String status,
    @Schema(description = "Original file name", example = "report.pdf")
    String fileName,
    @Schema(description = "File size in bytes", example = "1048576")
    long fileSize,
    @Schema(description = "Caption/description sent to Telegram", example = "My file", nullable = true)
    String caption,
    @Schema(description = "Additional message (error or info)", example = "File uploaded successfully")
    String message,
    @Schema(description = "Indicates whether the upload was deduplicated and reused an existing Telegram file", example = "false")
    boolean deduplicated,
    @Schema(description = "Logical file identifier", example = "d4a4638c-1d02-4e86-a830-6af48871f6f5", nullable = true)
    String logicalFileId,
    @Schema(description = "Logical version created or reused", example = "2")
    int version,
    @Schema(description = "SHA-256 checksum", example = "0f42be7ea1f9e5f6cc91d5c7f9d8f5d4cbd4b6e44c0ae91122c11ca4e7bcb6d9", nullable = true)
    String checksumSha256,
    @Schema(description = "Virtual path assigned to the file", example = "/projects/2026")
    String virtualPath,
    @Schema(description = "Logical tags assigned to the file")
    List<String> tags
) {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_UPLOADING = "UPLOADING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    public UploadResponse(
        long messageId,
        long chatId,
        String status,
        String fileName,
        long fileSize,
        String caption,
        String message
    ) {
        this(
            messageId,
            chatId,
            status,
            fileName,
            fileSize,
            caption,
            message,
            false,
            null,
            1,
            null,
            "/",
            List.of()
        );
    }
}
