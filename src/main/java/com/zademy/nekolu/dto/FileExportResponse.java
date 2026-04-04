/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * DTO for file export.
 */
@Schema(description = "Exported file list")
public record FileExportResponse(
    @Schema(description = "Export format", example = "json", allowableValues = {"json", "csv"})
    String format,

    @Schema(description = "Total exported files", example = "1500")
    int totalFiles,

    @Schema(description = "Exported file list")
    List<FileInfoResponse> files,

    @Schema(description = "Generation timestamp", example = "1678886400")
    long generatedAt
) {}
