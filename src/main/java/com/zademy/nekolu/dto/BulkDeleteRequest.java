/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;

/**
 * Request payload for deleting multiple Telegram file messages in a single operation.
 */
@Schema(description = "Request for deleting multiple Telegram messages/files in batch")
public record BulkDeleteRequest(
    @NotEmpty(message = "items must not be empty")
    @Schema(description = "List of files to delete", requiredMode = Schema.RequiredMode.REQUIRED)
    List<DeleteItem> items,

    @Schema(description = "Deletion type for all files: true = permanent (for everyone), false = local only",
            example = "false",
            allowableValues = {"true", "false"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    boolean permanent
) {
    @Schema(description = "Individual deletion item")
    public record DeleteItem(
        @Schema(description = "Message ID to delete", example = "123456789", requiredMode = Schema.RequiredMode.REQUIRED)
        long messageId,

        @Schema(description = "Chat ID containing the message", example = "261468790", requiredMode = Schema.RequiredMode.REQUIRED)
        long chatId,

        @Schema(description = "File ID associated with the message", example = "12345", requiredMode = Schema.RequiredMode.REQUIRED)
        int fileId,

        @Schema(description = "File name", example = "photo_12345.jpg")
        String fileName
    ) {}
}
