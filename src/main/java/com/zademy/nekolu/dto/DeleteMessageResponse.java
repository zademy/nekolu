/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response payload returned after attempting to delete a single Telegram message.
 */
@Schema(description = "Message/file deletion response")
public record DeleteMessageResponse(
    @Schema(description = "Deleted message ID", example = "123456789")
    long messageId,

    @Schema(description = "Chat ID", example = "261468790")
    long chatId,

    @Schema(description = "Operation status", example = "SUCCESS", allowableValues = {"SUCCESS", "FAILED"})
    String status,

    @Schema(description = "Deletion type performed", example = "LOCAL", allowableValues = {"LOCAL", "PERMANENT"})
    String deletionType,

    @Schema(description = "Result message", example = "Message deleted successfully")
    String message
) {
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String DELETION_TYPE_LOCAL = "LOCAL";
    public static final String DELETION_TYPE_PERMANENT = "PERMANENT";
}
