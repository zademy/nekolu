/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response payload for logical file-management actions such as move, archive, restore, and trash updates.
 */
@Schema(description = "Response for logical file actions")
public record FileActionResponse(
    @Schema(description = "File ID", example = "12345")
    long fileId,
    @Schema(description = "Operation status", example = "SUCCESS", allowableValues = {"SUCCESS", "FAILED"})
    String status,
    @Schema(description = "Informational message", example = "File restored successfully")
    String message,
    @Schema(description = "Current virtual path", example = "/projects/2026")
    String virtualPath,
    @Schema(description = "Indicates whether the file is archived", example = "false")
    boolean archived,
    @Schema(description = "Indicates whether the file is in trash", example = "false")
    boolean trashed,
    @Schema(description = "Current logical version", example = "3")
    int version
) {
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
}
