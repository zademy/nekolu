/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for TDLib network usage statistics.
 */
@Schema(description = "TDLib network usage statistics")
public record NetworkStatsResponse(
    @Schema(description = "Unix timestamp from which statistics are collected", example = "1704067200")
    int sinceDate,

    @Schema(description = "Network usage entries by file type")
    List<NetworkFileEntry> fileEntries,

    @Schema(description = "Network usage entries for calls")
    List<NetworkCallEntry> callEntries
) {
    /**
     * DTO for network usage in file transfers.
     */
    @Schema(description = "Network usage for file transfers")
    public record NetworkFileEntry(
        @Schema(description = "File type: photo, video, audio, document, etc.", example = "photo")
        String fileType,

        @Schema(description = "Network type: mobile, wifi, other", example = "wifi")
        String networkType,

        @Schema(description = "Sent bytes", example = "1048576")
        long sentBytes,

        @Schema(description = "Received bytes", example = "5242880")
        long receivedBytes
    ) {}

    /**
     * DTO for network usage in calls.
     */
    @Schema(description = "Network usage for calls")
    public record NetworkCallEntry(
        @Schema(description = "Network type: mobile, wifi, other", example = "wifi")
        String networkType,

        @Schema(description = "Sent bytes", example = "102400")
        long sentBytes,

        @Schema(description = "Received bytes", example = "204800")
        long receivedBytes
    ) {}
}
