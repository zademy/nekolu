/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for TDLib local storage statistics.
 */
@Schema(description = "Quick TDLib local storage statistics")
public record StorageStatsResponse(
    @Schema(description = "Approximate file size in bytes", example = "1073741824")
    long filesSize,

    @Schema(description = "Approximate file count", example = "500")
    int fileCount,

    @Schema(description = "SQLite database size in bytes", example = "10485760")
    long databaseSize,

    @Schema(description = "Language pack database size in bytes", example = "524288")
    long languagePackDbSize,

    @Schema(description = "TDLib log size in bytes", example = "2097152")
    long logSize
) {}
