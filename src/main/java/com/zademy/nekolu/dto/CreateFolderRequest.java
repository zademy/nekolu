/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request for creating a new folder (private Telegram channel).
 */
@Schema(description = "Request for creating a new folder")
public record CreateFolderRequest(
    @NotBlank(message = "Folder name is required")
    @Size(max = 128, message = "Folder name cannot exceed 128 characters")
    @Schema(description = "Folder name", example = "My Photos", requiredMode = Schema.RequiredMode.REQUIRED)
    String title,

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    @Schema(description = "Optional folder description", example = "Vacation photos 2024")
    String description
) {}
