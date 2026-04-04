/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for file streaming response.
 */
@Schema(description = "Information for file streaming")
public record FileStreamResponse(
    @Schema(description = "File ID", example = "12345")
    long fileId,

    @Schema(description = "File name", example = "video_12345.mp4")
    String fileName,

    @Schema(description = "Tipo MIME", example = "video/mp4")
    String mimeType,

    @Schema(description = "File size in bytes", example = "10485760")
    long fileSize,

    @Schema(description = "Relative streaming URL", example = "/api/telegram/files/12345/stream")
    String streamUrl,

    @Schema(description = "Indicates whether the file is available for streaming", example = "true")
    boolean available
) {}
