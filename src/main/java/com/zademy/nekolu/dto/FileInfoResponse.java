/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Detailed API representation of a Telegram-backed file, including TDLib metadata and logical drive metadata.
 */
@Schema(description = "Detailed information about a Telegram file")
public record FileInfoResponse(
    @Schema(description = "ID of the message containing the file", example = "123456789")
    long messageId,

    @Schema(description = "ID of the chat containing the file", example = "261468790")
    long chatId,

    @Schema(description = "Unique Telegram file ID", example = "12345")
    long fileId,

    @Schema(description = "File name", example = "photo_12345.jpg")
    String fileName,

    @Schema(description = "File size in bytes", example = "204800")
    long fileSize,

    @Schema(description = "File MIME type", example = "image/jpeg")
    String mimeType,

    @Schema(description = "File type", example = "PHOTO", allowableValues = {"PHOTO", "VIDEO", "AUDIO", "DOCUMENT", "VOICE", "VIDEO_NOTE", "FILE"})
    String type,

    @Schema(description = "Width in pixels (photos and videos only)", example = "1920", nullable = true)
    Integer width,

    @Schema(description = "Height in pixels (photos and videos only)", example = "1080", nullable = true)
    Integer height,

    @Schema(description = "Duration in seconds (audio, video, and voice notes only)", example = "60", nullable = true)
    Integer duration,

    @Schema(description = "Thumbnail path if present", example = "/tdlib/thumbs/photo_12345_thumb.jpg", nullable = true)
    String thumbnailPath,

    @Schema(description = "Message date as Unix timestamp", example = "1678886400")
    long date,

    @Schema(description = "Indicates whether the file is already downloaded locally", example = "true")
    boolean isDownloaded,

    @JsonIgnore
    @Schema(hidden = true)
    String localPath,

    @Schema(description = "Indicates whether the file is ready for direct download", example = "true")
    boolean downloadReady,

    @Schema(description = "Internal download URL", example = "/api/telegram/files/12345/content", nullable = true)
    String downloadUrl,

    @Schema(description = "Internal streaming URL", example = "/api/telegram/files/12345/stream")
    String streamUrl,

    @Schema(description = "Internal thumbnail URL", example = "/api/telegram/files/12345/thumbnail", nullable = true)
    String thumbnailUrl,

    @Schema(description = "Logical file identifier", example = "d4a4638c-1d02-4e86-a830-6af48871f6f5")
    String logicalFileId,

    @Schema(description = "Virtual path inside the drive", example = "/projects/2026")
    String virtualPath,

    @Schema(description = "Logical tags assigned to the file")
    List<String> tags,

    @Schema(description = "SHA-256 checksum", example = "0f42be7ea1f9e5f6cc91d5c7f9d8f5d4cbd4b6e44c0ae91122c11ca4e7bcb6d9", nullable = true)
    String checksumSha256,

    @Schema(description = "Logical version number", example = "2")
    int version,

    @Schema(description = "Logical last modification time as Unix timestamp in milliseconds", example = "1775260000000")
    long logicalModifiedAt,

    @Schema(description = "Logical origin of the file", example = "telegram-upload")
    String origin,

    @Schema(description = "Indicates whether the file is archived", example = "false")
    boolean archived,

    @Schema(description = "Indicates whether the file is in trash", example = "false")
    boolean trashed
) {
    public FileInfoResponse(
        long messageId,
        long chatId,
        long fileId,
        String fileName,
        long fileSize,
        String mimeType,
        String type,
        Integer width,
        Integer height,
        Integer duration,
        String thumbnailPath,
        long date,
        boolean isDownloaded,
        String localPath
    ) {
        this(
            messageId,
            chatId,
            fileId,
            fileName,
            fileSize,
            mimeType,
            type,
            width,
            height,
            duration,
            thumbnailPath,
            date,
            isDownloaded,
            localPath,
            isDownloaded,
            isDownloaded ? "/api/telegram/files/" + fileId + "/content" : null,
            "/api/telegram/files/" + fileId + "/stream",
            thumbnailPath != null && !thumbnailPath.isBlank() ? "/api/telegram/files/" + fileId + "/thumbnail" : null,
            "telegram-" + fileId,
            "/",
            List.of(),
            null,
            1,
            date * 1000L,
            "telegram-upload",
            false,
            false
        );
    }
}
