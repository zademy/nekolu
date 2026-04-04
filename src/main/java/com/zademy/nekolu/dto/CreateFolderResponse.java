/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response after creating a folder.
 */
@Schema(description = "Response with the result of creating a folder")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateFolderResponse(
    @Schema(description = "Created chat/channel ID", example = "-1001234567890")
    Long chatId,

    @Schema(description = "Created folder name", example = "My Photos")
    String title,

    @Schema(description = "Folder description", example = "Vacation photos 2024")
    String description,

    @Schema(description = "Indicates whether the operation was successful", example = "true")
    boolean success,

    @Schema(description = "Result or error message", example = "Folder created successfully")
    String message
) {}
