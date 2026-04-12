/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;

import com.zademy.nekolu.dto.BulkDeleteRequest;
import com.zademy.nekolu.dto.BulkDeleteResponse;
import com.zademy.nekolu.dto.DeleteMessageRequest;
import com.zademy.nekolu.dto.DeleteMessageResponse;
import com.zademy.nekolu.dto.DownloadFilesRequest;
import com.zademy.nekolu.dto.DownloadJob;
import com.zademy.nekolu.dto.DownloadProgress;
import com.zademy.nekolu.dto.DownloadResponse;
import com.zademy.nekolu.dto.FileActionResponse;
import com.zademy.nekolu.dto.FileExportResponse;
import com.zademy.nekolu.dto.FileInfoResponse;
import com.zademy.nekolu.dto.FileStatsResponse;
import com.zademy.nekolu.dto.FileStreamResponse;
import com.zademy.nekolu.dto.FullStatsResponse;
import com.zademy.nekolu.dto.MoveFileRequest;
import com.zademy.nekolu.dto.UploadResponse;
import com.zademy.nekolu.service.FileService;
import com.zademy.nekolu.service.TelegramService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Exposes REST endpoints for browsing, uploading, downloading, previewing, and organizing Telegram-backed files.
 */
@RestController
@RequestMapping("/api/telegram/files")
@Tag(name = "Files", description = "Telegram-backed file discovery, transfer, preview, and logical organization")
public class FileController {
    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    private final FileService fileService;
    private final TelegramService telegramService;

    public FileController(FileService fileService, TelegramService telegramService) {
        this.fileService = fileService;
        this.telegramService = telegramService;
    }

    @GetMapping
    @Operation(
        summary = "List files with advanced filters",
        description = """
            Lists Telegram files with support for advanced filters, pagination, and sorting.

            **File types:** all, photo, video, audio, document, voice, video_note

            **Sorting:** date_asc, date_desc, size_asc, size_desc, name_asc, name_desc

            **Filters:**
            - Date range (minDate, maxDate) in Unix timestamp
            - Size range (minSize, maxSize) in bytes
            - Specific chat (chatId)
            - Name search (filenameContains)
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File list retrieved successfully",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = FileInfoResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Invalid parameters or error while querying files")
    })
    public CompletableFuture<ResponseEntity<List<FileInfoResponse>>> listFiles(
            @RequestParam(required = false, defaultValue = "all")
            @Parameter(description = "File type", example = "photo",
                schema = @Schema(allowableValues = {"all", "photo", "video", "audio", "document", "voice", "video_note"}))
            String type,

            @RequestParam(required = false, defaultValue = "50")
            @Parameter(description = "Result limit (1-100)", example = "50",
                schema = @Schema(minimum = "1", maximum = "100"))
            Integer limit,

            @RequestParam(required = false, defaultValue = "")
            @Parameter(description = "Pagination cursor (offset string)", example = "")
            String offset,

            @RequestParam(required = false)
            @Parameter(description = "Sorting", example = "date_desc",
                schema = @Schema(allowableValues = {"date_asc", "date_desc", "size_asc", "size_desc", "name_asc", "name_desc"}))
            String sort,

            @RequestParam(required = false)
            @Parameter(description = "Minimum date (Unix timestamp)", example = "1678886400")
            Long minDate,

            @RequestParam(required = false)
            @Parameter(description = "Maximum date (Unix timestamp)", example = "1700000000")
            Long maxDate,

            @RequestParam(required = false)
            @Parameter(description = "Minimum size in bytes", example = "1048576")
            Long minSize,

            @RequestParam(required = false)
            @Parameter(description = "Maximum size in bytes", example = "1073741824")
            Long maxSize,

            @RequestParam(required = false)
            @Parameter(description = "Specific chat ID", example = "261468790")
            Long chatId,

            @RequestParam(required = false)
            @Parameter(description = "Search in file name", example = "report")
            String filenameContains) {

        if (limit == null || limit < 1 || limit > 100) {
            limit = 50;
        }

        return fileService.searchFilesAdvanced(type, limit, offset, sort,
                minDate, maxDate, minSize, maxSize, chatId, filenameContains)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest().body(null));
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "Get file information",
        description = "Gets detailed metadata for a specific file")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Information retrieved",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileInfoResponse.class))),
        @ApiResponse(responseCode = "404", description = "File not found"),
        @ApiResponse(responseCode = "400", description = "Invalid ID or error querying the file")
    })
    public CompletableFuture<ResponseEntity<FileInfoResponse>> getFileInfo(
            @PathVariable @Parameter(description = "File ID", example = "12345") long fileId) {
        return fileService.getFileInfo(fileId)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{fileId}/content")
    @Operation(summary = "Download file content",
        description = """
            Downloads the binary content of the file. The file must be downloaded beforehand.

            **Response headers:**
            - `Content-Type`: actual file MIME type (image/jpeg, video/mp4, audio/mpeg, etc.)
            - `Content-Disposition: attachment; filename="photo_1241.jpg"` with the correct name and extension

            If the file was previously listed, rich metadata is used (name, type, mime).
            Otherwise, a fallback filename is generated from the file type and MIME type.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Downloaded file content with the resolved filename and content type",
            content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary"))),
        @ApiResponse(responseCode = "404", description = "File not found or not downloaded")
    })
    public CompletableFuture<ResponseEntity<org.springframework.core.io.Resource>> downloadFileContent(
            @PathVariable @Parameter(description = "File ID", example = "12345") long fileId) {
        return fileService.streamFile(fileId).thenCompose(resource ->
            fileService.getFileInfo(fileId)
                .thenApply(fileInfo -> buildFileResponse(resource, fileInfo, "attachment"))
                .exceptionally(ex -> buildFallbackFileResponse(resource, fileId, "attachment"))
        ).exceptionally(ex -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{fileId}/view")
    @Operation(summary = "View file content",
        description = """
            Returns file content with `Content-Disposition: inline` so compatible file types
            (images, PDFs, audio, and video) can open directly in the browser.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File content ready for inline rendering",
            content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary"))),
        @ApiResponse(responseCode = "404", description = "File not found")
    })
    public CompletableFuture<ResponseEntity<org.springframework.core.io.Resource>> viewFileContent(
            @PathVariable @Parameter(description = "File ID", example = "12345") long fileId) {
        return fileService.streamFile(fileId).thenCompose(resource ->
            fileService.getFileInfo(fileId)
                .thenApply(fileInfo -> buildFileResponse(resource, fileInfo, "inline"))
                .exceptionally(ex -> buildFallbackFileResponse(resource, fileId, "inline"))
        ).exceptionally(ex -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<org.springframework.core.io.Resource> buildFileResponse(
            org.springframework.core.io.Resource resource,
            FileInfoResponse fileInfo,
            String dispositionType) {
        String fileName = resolveFileName(fileInfo);
        String mimeType = fileInfo.mimeType() != null ? fileInfo.mimeType() : "application/octet-stream";
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(mimeType);
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Content-Disposition", dispositionType + "; filename=\"" + fileName + "\"")
                .body(resource);
    }

    private ResponseEntity<org.springframework.core.io.Resource> buildFallbackFileResponse(
            org.springframework.core.io.Resource resource,
            long fileId,
            String dispositionType) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", dispositionType + "; filename=\"file_" + fileId + "\"")
                .body(resource);
    }

    private static final Map<String, String> MIME_EXTENSIONS = Map.ofEntries(
        Map.entry("image/jpeg", ".jpg"),
        Map.entry("image/png", ".png"),
        Map.entry("image/gif", ".gif"),
        Map.entry("image/webp", ".webp"),
        Map.entry("image/bmp", ".bmp"),
        Map.entry("image/svg+xml", ".svg"),
        Map.entry("video/mp4", ".mp4"),
        Map.entry("video/quicktime", ".mov"),
        Map.entry("video/x-matroska", ".mkv"),
        Map.entry("video/webm", ".webm"),
        Map.entry("video/x-msvideo", ".avi"),
        Map.entry("audio/mpeg", ".mp3"),
        Map.entry("audio/ogg", ".ogg"),
        Map.entry("audio/wav", ".wav"),
        Map.entry("audio/x-m4a", ".m4a"),
        Map.entry("audio/mp4", ".m4a"),
        Map.entry("application/pdf", ".pdf"),
        Map.entry("application/zip", ".zip"),
        Map.entry("application/x-rar-compressed", ".rar"),
        Map.entry("application/gzip", ".gz"),
        Map.entry("text/plain", ".txt"),
        Map.entry("text/html", ".html"),
        Map.entry("text/csv", ".csv"),
        Map.entry("application/json", ".json"),
        Map.entry("application/vnd.android.package-archive", ".apk"),
        Map.entry("application/octet-stream", "")
    );

    private String resolveFileName(FileInfoResponse fileInfo) {
        if (fileInfo.fileName() != null && !fileInfo.fileName().isBlank()) {
            return fileInfo.fileName();
        }
        String ext = "";
        if (fileInfo.mimeType() != null) {
            ext = MIME_EXTENSIONS.getOrDefault(fileInfo.mimeType().toLowerCase(), "");
            if (ext.isEmpty() && fileInfo.mimeType().contains("/")) {
                ext = "." + fileInfo.mimeType().split("/")[1].replaceAll("[^a-zA-Z0-9]", "");
            }
        }
        String prefix = switch (fileInfo.type() != null ? fileInfo.type().toLowerCase() : "") {
            case "photo" -> "photo";
            case "video" -> "video";
            case "audio" -> "audio";
            case "voice" -> "voice";
            case "video_note" -> "videonote";
            case "document" -> "document";
            default -> "file";
        };
        return prefix + "_" + fileInfo.fileId() + ext;
    }

    @GetMapping("/{fileId}/thumbnail")
    @Operation(
        summary = "Get thumbnail",
        description = """
            Returns the thumbnail of a file. For photos it uses the TDLib thumbnail (smallest size).
            For videos it uses the embedded thumbnail if available.
            If there is no thumbnail but the file is a downloaded photo/video, it returns the full file.

            **Response headers:**
            - `Content-Type: image/jpeg` for thumbnails
            - `Cache-Control: public, max-age=3600` to optimize loading
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thumbnail retrieved successfully",
            content = @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary"))),
        @ApiResponse(responseCode = "404", description = "Thumbnail not available")
    })
    public CompletableFuture<ResponseEntity<org.springframework.core.io.Resource>> getThumbnail(
            @PathVariable @Parameter(description = "File ID", example = "12345") long fileId) {
        return fileService.getThumbnailResource(fileId);
    }

    @GetMapping("/{fileId}/stream")
    @Operation(summary = "Streaming URL",
        description = "Gets streaming information for the file, including the direct URL")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Streaming information retrieved",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileStreamResponse.class))),
        @ApiResponse(responseCode = "404", description = "File not found or streaming unavailable")
    })
    public CompletableFuture<ResponseEntity<FileStreamResponse>> getStreamInfo(
            @PathVariable @Parameter(description = "File ID", example = "12345") long fileId) {
        return fileService.getStreamInfo(fileId)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{fileId}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Download progress (SSE)",
        description = "Server-Sent Events stream with real-time download progress")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Progress stream in text/event-stream format",
            content = @Content(mediaType = "text/event-stream", schema = @Schema(implementation = DownloadProgress.class)))
    })
    public SseEmitter downloadProgress(
            @PathVariable @Parameter(description = "File ID", example = "12345") long fileId) {
        return fileService.subscribeToProgress(fileId);
    }

    @PostMapping("/{fileId}/download")
    @Operation(summary = "Start download",
        description = "Starts the file download and returns immediately with PENDING status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Download started",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DownloadResponse.class))),
        @ApiResponse(responseCode = "400", description = "Error starting the download",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DownloadResponse.class)))
    })
    public CompletableFuture<ResponseEntity<DownloadResponse>> downloadFile(
            @PathVariable @Parameter(description = "File ID", example = "12345") long fileId) {
        return fileService.downloadFile(fileId)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest().body(
                        new DownloadResponse(fileId, DownloadResponse.STATUS_FAILED, null, 0, ex.getMessage())
                ));
    }

    @PostMapping("/download")
    @Operation(summary = "Download multiple files",
        description = "Starts downloading multiple files simultaneously")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Downloads started",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DownloadResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Invalid request (empty list) or error starting downloads")
    })
    public CompletableFuture<ResponseEntity<List<DownloadResponse>>> downloadFiles(
            @RequestBody @Parameter(description = "Request with fileId list", required = true)
            @Valid DownloadFilesRequest request) {
        return fileService.downloadFiles(request.fileIds())
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest().body(null));
    }

    @PostMapping("/batch-download")
    @Operation(summary = "Create batch download job",
        description = "Creates a job for downloading many files and monitoring overall progress")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Job created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DownloadJob.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request (empty list)")
    })
    public ResponseEntity<DownloadJob> createBatchDownload(
            @RequestBody @Parameter(description = "Request with fileId list", required = true)
            @Valid DownloadFilesRequest request) {
        DownloadJob job = fileService.createBatchDownloadJob(request.fileIds());
        return ResponseEntity.ok(job);
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Batch job status",
        description = "Checks the status and progress of a batch download job")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status retrieved",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DownloadJob.class))),
        @ApiResponse(responseCode = "404", description = "Job not found")
    })
    public ResponseEntity<DownloadJob> getJobStatus(
            @PathVariable @Parameter(description = "Job ID", example = "abc123") String jobId) {
        DownloadJob job = fileService.getBatchJobStatus(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }

    @GetMapping("/stats")
    @Operation(summary = "File statistics",
        description = "Gets aggregated statistics: totals, by type, by chat, downloaded vs pending")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Statistics retrieved",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileStatsResponse.class))),
        @ApiResponse(responseCode = "400", description = "Error retrieving statistics")
    })
    public CompletableFuture<ResponseEntity<FileStatsResponse>> getStats() {
        return fileService.getFileStats()
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest().build());
    }

    @GetMapping("/stats/full")
    @Operation(
        summary = "Full statistics",
        description = """
            Returns full statistics including files, local storage,
            network/bandwidth usage, and Telegram account limits.
            """,
        responses = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FullStatsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Error retrieving statistics")
        }
    )
    public CompletableFuture<ResponseEntity<FullStatsResponse>> getFullStats() {
        var fileStatsFuture = fileService.getFileStats();
        var storageStatsFuture = telegramService.getStorageStatisticsFast();
        var networkStatsFuture = telegramService.getNetworkStatistics(false);
        var limitsFuture = telegramService.getTelegramLimits();

        return CompletableFuture.allOf(fileStatsFuture, storageStatsFuture, networkStatsFuture, limitsFuture)
            .thenApply(v -> ResponseEntity.ok(new FullStatsResponse(
                fileStatsFuture.join(),
                storageStatsFuture.join(),
                networkStatsFuture.join(),
                limitsFuture.join()
            )))
            .exceptionally(ex -> {
                logger.error("Error getting full statistics: {}", ex.getMessage());
                return ResponseEntity.badRequest().build();
            });
    }

    @GetMapping("/export")
    @Operation(summary = "Export file list",
        description = "Exports the full file list in JSON or CSV format")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Export generated",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileExportResponse.class))),
        @ApiResponse(responseCode = "400", description = "Error exporting files")
    })
    public CompletableFuture<ResponseEntity<FileExportResponse>> exportFiles(
            @RequestParam(required = false, defaultValue = "json")
            @Parameter(description = "Export format", example = "json",
                schema = @Schema(allowableValues = {"json", "csv"}))
            String format,

            @RequestParam(required = false)
            @Parameter(description = "File type to filter", example = "photo")
            String type) {
        return fileService.exportFiles(format, type)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest().build());
    }

    @GetMapping("/recent")
    @Operation(summary = "Recent Saved Messages files",
        description = """
                Gets the latest files from your personal chat (Saved Messages).
                Includes recently uploaded files and returns an empty list if the underlying Telegram query fails.
                """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recent Saved Messages files retrieved",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = FileInfoResponse.class))))
    })
    public CompletableFuture<ResponseEntity<List<FileInfoResponse>>> getRecentFiles(
            @RequestParam(required = false, defaultValue = "50")
            @Parameter(description = "Result limit", example = "50")
            Integer limit) {
        return fileService.getRecentFilesFromOwnChat(limit != null ? limit : 50)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> {
                    logger.error("[RecentFiles] Error: {}", ex.getMessage());
                    return ResponseEntity.ok(List.of());
                });
    }

    /**
     * Documents the multipart fields accepted by the upload endpoint.
     */
    @Schema(name = "UploadFileMultipartRequest", description = "Multipart form for uploading a file to Telegram")
    public record UploadFileMultipartRequest(
        @Schema(description = "File to upload", type = "string", format = "binary")
        String file,
        @Schema(description = "Target chat ID (optional - uses Saved Messages by default)", example = "12345678", nullable = true)
        Long chatId,
        @Schema(description = "File description/caption", example = "My file", nullable = true)
        String caption,
        @Schema(description = "Upload type", example = "document", allowableValues = {"document", "photo"})
        String type,
        @Schema(description = "Virtual path to assign inside the drive", example = "/projects/2026", nullable = true)
        String virtualPath,
        @Schema(description = "Comma-separated tags", example = "work,backup,invoice", nullable = true)
        String tags,
        @Schema(description = "Logical origin of the file", example = "telegram-upload", nullable = true)
        String origin,
        @Schema(description = "Whether the file should start archived", example = "false", nullable = true)
        Boolean archived
    ) {}

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload file to Telegram",
        description = """
            Uploads a local file to Telegram as a document or photo.
            The file is sent to the authenticated user's Saved Messages chat by default.
            Optional logical metadata such as virtual path, tags, origin, and archive state can be attached at upload time.
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(mediaType = "multipart/form-data",
                schema = @Schema(implementation = UploadFileMultipartRequest.class))
        ))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File uploaded successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UploadResponse.class))),
        @ApiResponse(responseCode = "400", description = "Upload error - invalid parameters or file does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UploadResponse.class)))
    })
    public CompletableFuture<ResponseEntity<UploadResponse>> uploadFile(
            @RequestParam("file")
            @Parameter(description = "File to upload", required = true)
            org.springframework.web.multipart.MultipartFile file,

            @RequestParam(value = "chatId", required = false)
            @Parameter(description = "Target chat ID (optional - uses Saved Messages by default)", example = "12345678")
            Long chatId,

            @RequestParam(value = "caption", required = false)
            @Parameter(description = "File description/caption", example = "My file")
            String caption,

            @RequestParam(value = "type", required = false, defaultValue = "document")
            @Parameter(description = "Upload type", example = "document",
                schema = @Schema(allowableValues = {"document", "photo"}))
            String type,

            @RequestParam(value = "virtualPath", required = false)
            @Parameter(description = "Virtual path inside the drive", example = "/projects/2026")
            String virtualPath,

            @RequestParam(value = "tags", required = false)
            @Parameter(description = "Comma-separated tags", example = "work,backup,invoice")
            String tags,

            @RequestParam(value = "origin", required = false)
            @Parameter(description = "Logical origin of the file", example = "telegram-upload")
            String origin,

            @RequestParam(value = "archived", required = false, defaultValue = "false")
            @Parameter(description = "Whether the file should start archived", example = "false")
            boolean archived) {

        CompletableFuture<Long> targetChatFuture = chatId != null && chatId != 0
            ? CompletableFuture.completedFuture(chatId)
            : fileService.getOwnChatId();

        return targetChatFuture.thenCompose(targetChatId -> {
            logger.info("[UploadController] Chat ID to use: {}", targetChatId);

            if (targetChatId == 0) {
                return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(
                        new UploadResponse(0, 0, UploadResponse.STATUS_FAILED,
                            file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown",
                            0, caption, "Could not resolve the user's chat ID")
                    )
                );
            }

            try {
                java.io.File stagedFile = createUploadStagingFile(file);

                logger.info("[UploadController] Upload staging file created: {}", stagedFile.getAbsolutePath());
                logger.info("[UploadController] Size: {} bytes", stagedFile.length());

                CompletableFuture<UploadResponse> uploadFuture;
                if ("photo".equalsIgnoreCase(type)) {
                    uploadFuture = fileService.uploadPhoto(
                        stagedFile,
                        targetChatId,
                        caption,
                        virtualPath,
                        parseTags(tags),
                        origin,
                        archived
                    );
                } else {
                    uploadFuture = fileService.uploadFile(
                        stagedFile,
                        targetChatId,
                        caption,
                        virtualPath,
                        parseTags(tags),
                        origin,
                        archived
                    );
                }

                return uploadFuture.thenApply(response -> {
                    logger.info("[UploadController] Upload completed - Status: {}, Message ID: {}, Chat ID: {}",
                        response.status(), response.messageId(), response.chatId());
                    // TelegramServiceImpl cleans the staged upload file once TDLib confirms the remote upload.
                    return ResponseEntity.ok(response);
                }).exceptionally(ex -> {
                    logger.error("[UploadController] Upload failed", ex);
                    stagedFile.delete();
                    return ResponseEntity.badRequest().body(
                        new UploadResponse(0, targetChatId, UploadResponse.STATUS_FAILED,
                            file.getOriginalFilename(), file.getSize(), caption, "Failure: " + ex.getMessage())
                    );
                });

            } catch (Exception e) {
                logger.error("[UploadController] Error processing file", e);
                return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(
                        new UploadResponse(0, targetChatId, UploadResponse.STATUS_FAILED,
                            file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown",
                            0, caption, "Error processing file: " + e.getMessage())
                    )
                );
            }
        }).exceptionally(ex -> {
            logger.error("[UploadController] Error getting chat", ex);
            return ResponseEntity.badRequest().body(
                new UploadResponse(0, 0, UploadResponse.STATUS_FAILED,
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown",
                    0, caption, "Error resolving chat: " + ex.getMessage())
            );
        });
    }

    private java.io.File createUploadStagingFile(org.springframework.web.multipart.MultipartFile multipartFile) throws java.io.IOException {
        java.nio.file.Path stagingDir = java.nio.file.Paths.get("tdlib", "upload-staging");
        java.nio.file.Files.createDirectories(stagingDir);

        String originalName = multipartFile.getOriginalFilename() != null ? multipartFile.getOriginalFilename() : "upload.bin";
        String sanitizedName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitizedName.isBlank()) {
            sanitizedName = "upload.bin";
        }

        java.nio.file.Path stagedPath = stagingDir.resolve(java.util.UUID.randomUUID() + "_" + sanitizedName);
        multipartFile.transferTo(stagedPath);
        return stagedPath.toFile();
    }

    @PostMapping("/{fileId}/restore")
    @Operation(summary = "Restore file from trash", description = "Restores a file from the logical trash")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File restored",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileActionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Restore error")
    })
    public CompletableFuture<ResponseEntity<FileActionResponse>> restoreFile(
            @PathVariable @Parameter(description = "File ID", example = "12345") long fileId) {
        return fileService.restoreFile(fileId)
            .thenApply(ResponseEntity::ok)
            .exceptionally(ex -> ResponseEntity.badRequest().body(
                new FileActionResponse(fileId, FileActionResponse.STATUS_FAILED, ex.getMessage(), "/", false, false, 0)
            ));
    }

    @PostMapping("/{fileId}/move")
    @Operation(summary = "Move file logically", description = "Moves a file to a virtual path inside the drive")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File moved",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileActionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Move error")
    })
    public CompletableFuture<ResponseEntity<FileActionResponse>> moveFile(
            @PathVariable @Parameter(description = "File ID", example = "12345") long fileId,
            @RequestBody @Valid MoveFileRequest request) {
        return fileService.moveFile(fileId, request.virtualPath())
            .thenApply(ResponseEntity::ok)
            .exceptionally(ex -> ResponseEntity.badRequest().body(
                new FileActionResponse(fileId, FileActionResponse.STATUS_FAILED, ex.getMessage(), "/", false, false, 0)
            ));
    }

    @PostMapping("/{fileId}/archive")
    @Operation(summary = "Archive or unarchive file", description = "Marks a file as archived or active in the logical index")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Archive state updated",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileActionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Archive error")
    })
    public CompletableFuture<ResponseEntity<FileActionResponse>> archiveFile(
            @PathVariable @Parameter(description = "File ID", example = "12345") long fileId,
            @RequestParam(defaultValue = "true") boolean archived) {
        return fileService.archiveFile(fileId, archived)
            .thenApply(ResponseEntity::ok)
            .exceptionally(ex -> ResponseEntity.badRequest().body(
                new FileActionResponse(fileId, FileActionResponse.STATUS_FAILED, ex.getMessage(), "/", false, false, 0)
            ));
    }

    @GetMapping("/trash")
    @Operation(
        summary = "List logical trash",
        description = "Returns files currently stored in the logical trash. If the query fails, the endpoint returns an empty list."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Logical trash retrieved",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = FileInfoResponse.class))))
    })
    public CompletableFuture<ResponseEntity<List<FileInfoResponse>>> listTrash() {
        return fileService.listTrash()
            .thenApply(ResponseEntity::ok)
            .exceptionally(ex -> ResponseEntity.ok(List.of()));
    }

    @DeleteMapping("/message")
    @Operation(
        summary = "Delete Telegram message",
        description = """
            Deletes a Telegram message with two options:

            - **Local**: Deletes it only from the local view (the message remains visible to others)
            - **Permanent**: Deletes the message completely (for everyone in the chat)

            Requires the messageId and chatId of the message to delete.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Message deleted successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeleteMessageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Request error - invalid parameters",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeleteMessageResponse.class)))
    })
    public CompletableFuture<ResponseEntity<DeleteMessageResponse>> deleteMessage(
            @RequestBody @Parameter(description = "Request with deletion data", required = true)
            DeleteMessageRequest request) {

        if (request == null || request.messageId() == 0 || request.chatId() == 0) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body(
                new DeleteMessageResponse(0, 0, DeleteMessageResponse.STATUS_FAILED, "UNKNOWN",
                    "messageId and chatId are required")
            ));
        }

        return fileService.deleteMessage(request.messageId(), request.chatId(), request.permanent())
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest().body(
                    new DeleteMessageResponse(request.messageId(), request.chatId(),
                        DeleteMessageResponse.STATUS_FAILED,
                        request.permanent() ? DeleteMessageResponse.DELETION_TYPE_PERMANENT : DeleteMessageResponse.DELETION_TYPE_LOCAL,
                        "Error: " + ex.getMessage())
                ));
    }

    @DeleteMapping("/bulk-delete")
    @Operation(
        summary = "Delete multiple Telegram messages",
        description = """
            Deletes multiple Telegram messages/files in a single operation.

            - **Local**: Deletes them only from the local view (messages remain visible to others)
            - **Permanent**: Deletes the messages completely (for everyone in the chat)

            The `permanent` field applies to ALL files in the batch.
            If one file fails, processing continues with the rest.

            Returns a summary with: total requested, successful, failed, and details for each operation.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Batch deletion completed",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BulkDeleteResponse.class))),
        @ApiResponse(responseCode = "400", description = "Request error - empty list or invalid parameters",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BulkDeleteResponse.class)))
    })
    public CompletableFuture<ResponseEntity<BulkDeleteResponse>> bulkDeleteMessages(
            @RequestBody @Parameter(description = "Request with files to delete", required = true)
            @Valid BulkDeleteRequest request) {

        return fileService.bulkDeleteMessages(request)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest().body(
                    new BulkDeleteResponse(
                        request.items().size(),
                        0,
                        request.items().size(),
                        List.of(new BulkDeleteResponse.DeleteResult(
                            0, null, false, "Error general: " + ex.getMessage()
                        ))
                    )
                ));
    }

    // ==================== FOLDER ENDPOINTS ====================

    @GetMapping("/folders/{chatId}/files")
    @Operation(
        summary = "List folder files",
        description = """
            Lists files from a specific folder/channel.
            Uses SearchChatMessages to search INSIDE the chat, allowing files to be found in private channels.

            **File types:** all, photo, video, audio, document, voice, video_note
            If the folder query fails, the endpoint falls back to an empty list.
            """,
        tags = {"Folders"}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Folder file list retrieved successfully",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = FileInfoResponse.class))))
    })
    public CompletableFuture<ResponseEntity<List<FileInfoResponse>>> listFolderFiles(
            @PathVariable
            @Parameter(description = "Chat/folder ID", example = "-1001234567890")
            long chatId,

            @RequestParam(required = false, defaultValue = "all")
            @Parameter(description = "File type", example = "photo",
                schema = @Schema(allowableValues = {"all", "photo", "video", "audio", "document", "voice", "video_note"}))
            String type,

            @RequestParam(required = false, defaultValue = "100")
            @Parameter(description = "Result limit (1-1000)", example = "100",
                schema = @Schema(minimum = "1", maximum = "1000"))
            Integer limit) {

        if (limit == null || limit < 1 || limit > 1000) {
            limit = 100;
        }

        return fileService.searchFilesInChat(chatId, type, limit)
            .thenApply(ResponseEntity::ok)
            .exceptionally(ex -> ResponseEntity.ok(Collections.emptyList()));
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(tags.split(","))
            .map(String::trim)
            .filter(tag -> !tag.isBlank())
            .distinct()
            .toList();
    }
}
