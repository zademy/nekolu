/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Request for downloading multiple files.
 */
@Schema(description = "Request to start downloading multiple files simultaneously")
public record DownloadFilesRequest(
    @Schema(description = "List of file IDs to download", example = "[12345, 12346, 12347]", requiredMode = Schema.RequiredMode.REQUIRED)
    List<Long> fileIds
) {}
