/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for moving a file to a different virtual path inside the drive.
 */
@Schema(description = "Request to move a file to a virtual path")
public record MoveFileRequest(
    @NotBlank(message = "virtualPath is required")
    @Schema(description = "Virtual path destination", example = "/projects/2026")
    String virtualPath
) {}
