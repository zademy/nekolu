/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for full statistics: files, storage, network, and limits.
 */
@Schema(description = "Full statistics: files, storage, network, and limits")
public record FullStatsResponse(
    @Schema(description = "File statistics")
    FileStatsResponse fileStats,

    @Schema(description = "Local storage statistics")
    StorageStatsResponse storageStats,

    @Schema(description = "Network usage statistics")
    NetworkStatsResponse networkStats,

    @Schema(description = "Account limits")
    TelegramLimitsResponse limits
) {}
