/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for Telegram account limits.
 */
@Schema(description = "Telegram account limits")
public record TelegramLimitsResponse(
    @Schema(description = "Maximum file upload size in bytes", example = "2147483648")
    long maxFileUploadSize,

    @Schema(description = "Maximum members in a basic group", example = "200")
    int maxBasicGroupSize,

    @Schema(description = "Maximum members in a supergroup", example = "200000")
    int maxSupergroupSize,

    @Schema(description = "Maximum number of chat folders", example = "30")
    int maxChatFolders
) {}
