/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request payload for deleting a single Telegram file message.
 */
@Schema(description = "Request for deleting a Telegram message/file")
public record DeleteMessageRequest(
    @Schema(description = "Message ID to delete", example = "123456789", requiredMode = Schema.RequiredMode.REQUIRED)
    long messageId,

    @Schema(description = "Chat ID containing the message", example = "261468790", requiredMode = Schema.RequiredMode.REQUIRED)
    long chatId,

    @Schema(description = "File ID associated with the message", example = "12345")
    Long fileId,

    @Schema(description = "Deletion type: true = permanent (for everyone), false = local only",
            example = "false",
            allowableValues = {"true", "false"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    boolean permanent
) {}
