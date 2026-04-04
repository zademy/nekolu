/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Information about a folder (private Telegram channel).
 */
@Schema(description = "Information about a folder/channel")
public record FolderInfo(
    @Schema(description = "Chat/channel ID", example = "-1001234567890")
    long chatId,

    @Schema(description = "Folder name", example = "My Photos")
    String title,

    @Schema(description = "Folder description", example = "Vacation photos 2024")
    String description,

    @Schema(description = "Number of channel members", example = "1")
    int memberCount,

    @Schema(description = "Creation date as timestamp", example = "1704067200")
    long createdDate
) {}
