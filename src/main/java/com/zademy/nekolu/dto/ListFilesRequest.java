/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request for listing files with filters.
 */
@Schema(description = "Parameters for filtering and paginating the file list")
public record ListFilesRequest(
    @Schema(description = "File type to filter", example = "photo", allowableValues = {"all", "photo", "video", "audio", "document", "voice", "video_note"})
    String type,

    @Schema(description = "Maximum number of results (1-100)", example = "50", minimum = "1", maximum = "100")
    Integer limit,

    @Schema(description = "Pagination offset (leave empty for the first page)", example = "")
    String offset
) {
    public ListFilesRequest {
        if (type == null || type.isBlank()) {
            type = "all";
        }
        if (limit == null || limit < 1 || limit > 100) {
            limit = 50;
        }
        if (offset == null) {
            offset = "";
        }
    }

    public ListFilesRequest() {
        this("all", 50, "");
    }
}
