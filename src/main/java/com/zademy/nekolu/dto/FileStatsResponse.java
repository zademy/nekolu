/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * DTO for file statistics.
 */
@Schema(description = "Telegram file statistics")
public record FileStatsResponse(
    @Schema(description = "Total files", example = "1500")
    long totalFiles,

    @Schema(description = "Total size in bytes", example = "2684354560")
    long totalSizeBytes,

    @Schema(description = "Distribution by file type")
    Map<String, Long> byType,

    @Schema(description = "Distribution by chat")
    Map<Long, Long> byChat,

    @Schema(description = "Downloaded file count", example = "800")
    long downloadedCount,

    @Schema(description = "Pending download file count", example = "700")
    long pendingCount,

    @Schema(description = "Total downloaded size in bytes", example = "1073741824")
    long downloadedSizeBytes
) {}
