/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response after deleting a folder.
 */
@Schema(description = "Response with the result of deleting a folder")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeleteFolderResponse(
    @Schema(description = "Deleted chat/channel ID", example = "-1001234567890")
    long chatId,

    @Schema(description = "Indicates whether the operation was successful", example = "true")
    boolean success,

    @Schema(description = "Result or error message", example = "Folder deleted successfully")
    String message
) {}
