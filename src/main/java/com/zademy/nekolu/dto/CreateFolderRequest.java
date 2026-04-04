/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request for creating a new folder (private Telegram channel).
 */
@Schema(description = "Request for creating a new folder")
public record CreateFolderRequest(
    @Schema(description = "Folder name", example = "My Photos", requiredMode = Schema.RequiredMode.REQUIRED)
    String title,

    @Schema(description = "Optional folder description", example = "Vacation photos 2024")
    String description
) {}
