/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.service.impl;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.zademy.nekolu.constants.FileTypeConstants;
import com.zademy.nekolu.constants.MediaConstants;
import com.zademy.nekolu.constants.ServiceDefaults;
import com.zademy.nekolu.dto.BulkDeleteRequest;
import com.zademy.nekolu.dto.BulkDeleteResponse;
import com.zademy.nekolu.dto.DeleteMessageResponse;
import com.zademy.nekolu.dto.DownloadJob;
import com.zademy.nekolu.dto.DownloadResponse;
import com.zademy.nekolu.dto.FileActionResponse;
import com.zademy.nekolu.dto.FileExportResponse;
import com.zademy.nekolu.dto.FileInfoResponse;
import com.zademy.nekolu.dto.FileStatsResponse;
import com.zademy.nekolu.dto.FileStreamResponse;
import com.zademy.nekolu.dto.UploadResponse;
import com.zademy.nekolu.model.TelegramFileMessage;
import com.zademy.nekolu.model.TelegramFileState;
import com.zademy.nekolu.service.FileService;
import com.zademy.nekolu.service.MetadataIndexService;
import com.zademy.nekolu.service.TelegramService;

/**
 * Implementation of the Telegram file management service.
 * Every Telegram interaction crosses the TelegramService seam in domain
 * types; this module owns workspace logic: search shaping, download
 * orchestration, upload responses, deletions, and logical enrichment.
 */
@Service
public class FileServiceImpl implements FileService {
    private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);
    private static final String CACHE_CONTROL_HEADER = "Cache-Control";
    private static final String CACHE_CONTROL_PUBLIC_MAX_AGE = "public, max-age=3600";

    private record AdvancedSearchOptions(
            String sort,
            int limit,
            Long minDate,
            Long maxDate,
            Long minSize,
            Long maxSize,
            Long chatId,
            String filenameContains) {}

    private final TelegramService telegramService;
    private final MetadataIndexService metadataIndexService;
    private final Cache<Long, FileInfoResponse> fileMetadataCache;

    public FileServiceImpl(
        TelegramService telegramService,
        MetadataIndexService metadataIndexService,
        @Qualifier("fileInfoNativeCache") Cache<Long, FileInfoResponse> fileMetadataCache
    ) {
        this.telegramService = telegramService;
        this.metadataIndexService = metadataIndexService;
        this.fileMetadataCache = fileMetadataCache;
    }

    private static final List<String> ALL_FILE_TYPES = FileTypeConstants.SEARCHABLE_TYPES;

    /**
     * Searches files inside a specific chat/folder.
     * Uses the seam's chat history so private channels (folders) are covered.
     *
     * @param chatId chat/folder ID to search in
     * @param type file type (photo, video, audio, document, voice, video_note, all)
     * @param limit result limit
     * @return list of matching files
     */
    @Override
    public CompletableFuture<List<FileInfoResponse>> searchFilesInChat(long chatId, String type, int limit) {
        return telegramService.getFileMessages(chatId, 0, Math.max(limit * 4, 100))
            .exceptionally(_ex -> List.of())
            .thenApply(files -> files.stream()
                .map(this::toFileInfo)
                .filter(file -> matchesRequestedType(file, type))
                .sorted(Comparator.comparingLong(FileInfoResponse::date).reversed())
                .limit(limit)
                .toList())
            .thenCompose(this::enrichVisibleFiles);
    }

    private boolean matchesRequestedType(FileInfoResponse file, String type) {
        if (type == null || type.isBlank() || type.equalsIgnoreCase(FileTypeConstants.ALL)) {
            return true;
        }
        return type.equalsIgnoreCase(file.type());
    }

    /**
     * Searches files by type across all chats.
     * When type is "all", it searches each type separately and combines results.
     */
    @Override
    public CompletableFuture<List<FileInfoResponse>> searchFilesByType(String type, int limit, String offset) {
        if (type == null || FileTypeConstants.ALL.equalsIgnoreCase(type)) {
            return searchAllTypes(limit);
        }

        return searchSingleType(type, limit, offset).thenCompose(this::enrichVisibleFiles);
    }

    private CompletableFuture<List<FileInfoResponse>> searchAllTypes(int limit) {
        int perType = Math.max(limit / ALL_FILE_TYPES.size(), ServiceDefaults.MIN_RESULTS_PER_TYPE);

        List<CompletableFuture<List<FileInfoResponse>>> futures = ALL_FILE_TYPES.stream()
            .map(t -> searchSingleType(t, perType, ""))
            .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .flatMap(f -> f.join().stream())
                .sorted(Comparator.comparingLong(FileInfoResponse::date).reversed())
                .limit(limit)
                .toList())
            .thenCompose(this::enrichVisibleFiles);
    }

    private CompletableFuture<List<FileInfoResponse>> searchSingleType(String type, int limit, String offset) {
        return telegramService.searchFileMessages("", type, offset, limit)
            .exceptionally(_ex -> List.of())
            .thenApply(found -> found.stream().map(this::toFileInfo).toList());
    }

    /**
     * Gets information about a specific file.
     */
    @Override
    public CompletableFuture<FileInfoResponse> getFileInfo(long fileId) {
        FileInfoResponse cached = fileMetadataCache.getIfPresent(fileId);
        if (cached != null) {
            return refreshDownloadStatus(cached).thenCompose(metadataIndexService::enrich);
        }

        return telegramService.getFileState(fileId)
            .thenApply(this::mapStateToFileInfo)
            .thenCompose(metadataIndexService::enrich);
    }

    private CompletableFuture<FileInfoResponse> refreshDownloadStatus(FileInfoResponse cached) {
        return telegramService.getFileState(cached.fileId())
            .exceptionally(_ex -> null)
            .thenApply(state -> {
                if (state == null) {
                    return cached;
                }
                FileInfoResponse updated = new FileInfoResponse(
                    cached.messageId(), cached.chatId(), cached.fileId(),
                    cached.fileName(), state.size() > 0 ? state.size() : cached.fileSize(),
                    cached.mimeType(), cached.type(),
                    cached.width(), cached.height(), cached.duration(),
                    cached.thumbnailPath(), cached.date(),
                    state.downloaded(),
                    state.localPath() != null && !state.localPath().isBlank() ? state.localPath() : cached.localPath()
                );
                fileMetadataCache.put(cached.fileId(), updated);
                return updated;
            });
    }

    private CompletableFuture<List<FileInfoResponse>> refreshDownloadStatuses(List<FileInfoResponse> files) {
        if (files == null || files.isEmpty()) {
            return CompletableFuture.completedFuture(files != null ? files : List.of());
        }

        List<CompletableFuture<FileInfoResponse>> futures = files.stream()
            .map(file -> refreshDownloadStatus(file).exceptionally(_ex -> file))
            .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(_ignored -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
    }

    /**
     * Starts a file download through the seam's single download contract and
     * returns immediately with PENDING status.
     * The client should query GET /{fileId} to check progress.
     */
    @Override
    public CompletableFuture<DownloadResponse> downloadFile(long fileId) {
        return getFileInfo(fileId).<DownloadResponse>thenCompose(fileInfo -> {
            // Check whether it is already downloaded
            if (fileInfo.isDownloaded() && fileInfo.localPath() != null) {
                return CompletableFuture.completedFuture(new DownloadResponse(
                    fileId,
                    DownloadResponse.STATUS_COMPLETED,
                    fileInfo.localPath(),
                    100,
                    "File already downloaded"
                ));
            }

            // If the file is still uploading, wait for the staged source to
            // be released before starting the download
            return awaitUploadRelease(fileId)
                .thenCompose(_v -> telegramService.startDownload(fileId))
                .thenApply(state -> new DownloadResponse(
                    fileId,
                    DownloadResponse.STATUS_PENDING,
                    null,
                    state.progressPercent(),
                    "Download started in background. Check GET /{fileId} for progress."
                ));
        }).exceptionally(ex -> new DownloadResponse(
            fileId,
            DownloadResponse.STATUS_FAILED,
            null,
            0,
            causeMessage(ex)
        ));
    }

    /**
     * Downloads multiple files.
     */
    @Override
    public CompletableFuture<List<DownloadResponse>> downloadFiles(List<Long> fileIds) {
        List<CompletableFuture<DownloadResponse>> futures = new ArrayList<>();

        for (Long fileId : fileIds) {
            futures.add(downloadFile(fileId));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<DownloadResponse> responses = new ArrayList<>();
                for (CompletableFuture<DownloadResponse> f : futures) {
                    try {
                        responses.add(f.join());
                    } catch (Exception e) {
                        responses.add(new DownloadResponse(
                            0,
                            DownloadResponse.STATUS_FAILED,
                            null,
                            0,
                            e.getMessage()
                        ));
                    }
                }
                return responses;
            });
    }

    /**
     * Maps a seam file message to the workspace response view. URL
     * construction and logical defaults happen inside the response record.
     */
    private FileInfoResponse toFileInfo(TelegramFileMessage message) {
        FileInfoResponse fileInfo = new FileInfoResponse(
            message.messageId(),
            message.chatId(),
            message.fileId(),
            message.fileName(),
            message.fileSize(),
            message.mimeType(),
            message.type(),
            message.width(),
            message.height(),
            message.duration(),
            message.thumbnailPath(),
            message.date(),
            message.downloaded(),
            message.localPath()
        );
        fileMetadataCache.put(fileInfo.fileId(), fileInfo);
        return fileInfo;
    }

    /**
     * Maps a seam file state to the workspace response view, deriving mime
     * and type from the local path when the state carries no message
     * context.
     */
    private FileInfoResponse mapStateToFileInfo(TelegramFileState state) {
        String localPath = state.localPath();
        String mimeType = guessMimeTypeFromPath(localPath);
        String type = guessTypeFromMime(mimeType);

        return new FileInfoResponse(
            0,
            0,
            state.fileId(),
            null,
            state.size(),
            mimeType,
            type,
            null,
            null,
            null,
            null,
            0,
            state.downloaded(),
            localPath
        );
    }

    private String guessMimeTypeFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return switch (extractFileExtension(path)) {
            case MediaConstants.EXTENSION_JPG, MediaConstants.EXTENSION_JPEG -> MediaConstants.MIME_IMAGE_JPEG;
            case MediaConstants.EXTENSION_PNG -> MediaConstants.MIME_IMAGE_PNG;
            case MediaConstants.EXTENSION_GIF -> MediaConstants.MIME_IMAGE_GIF;
            case MediaConstants.EXTENSION_WEBP -> MediaConstants.MIME_IMAGE_WEBP;
            case MediaConstants.EXTENSION_MP4 -> MediaConstants.MIME_VIDEO_MP4;
            case MediaConstants.EXTENSION_MOV -> MediaConstants.MIME_VIDEO_QUICKTIME;
            case MediaConstants.EXTENSION_MKV -> MediaConstants.MIME_VIDEO_MATROSKA;
            case MediaConstants.EXTENSION_MP3 -> MediaConstants.MIME_AUDIO_MPEG;
            case MediaConstants.EXTENSION_OGG -> MediaConstants.MIME_AUDIO_OGG;
            case MediaConstants.EXTENSION_M4A -> MediaConstants.MIME_AUDIO_MP4;
            case MediaConstants.EXTENSION_PDF -> MediaConstants.MIME_APPLICATION_PDF;
            case MediaConstants.EXTENSION_ZIP -> MediaConstants.MIME_APPLICATION_ZIP;
            case MediaConstants.EXTENSION_APK -> MediaConstants.MIME_APPLICATION_APK;
            default -> null;
        };
    }

    private String extractFileExtension(String path) {
        String normalizedPath = path.toLowerCase();
        int extensionStart = normalizedPath.lastIndexOf('.');
        if (extensionStart < 0) {
            return normalizedPath;
        }
        return normalizedPath.substring(extensionStart);
    }

    /**
     * Fallback classification for file states that carry no message context
     * (raw file lookups), derived from the local path's MIME type. The
     * primary message-content classification lives once in the Telegram
     * module; this only approximates when no message is available.
     */
    private String guessTypeFromMime(String mimeType) {
        if (mimeType == null) return FileTypeConstants.FILE_KIND;
        if (mimeType.startsWith(MediaConstants.MIME_IMAGE_PREFIX)) return FileTypeConstants.PHOTO_KIND;
        if (mimeType.startsWith(MediaConstants.MIME_VIDEO_PREFIX)) return FileTypeConstants.VIDEO_KIND;
        if (mimeType.startsWith(MediaConstants.MIME_AUDIO_PREFIX)) return FileTypeConstants.AUDIO_KIND;
        return FileTypeConstants.DOCUMENT_KIND;
    }

    // ==================== NEW V2 METHODS ====================

    private final ConcurrentHashMap<String, DownloadJob> batchJobs = new ConcurrentHashMap<>();

    /**
     * Advanced search with filters, sorting, and pagination.
     * Includes files from the user's own chat (Saved Messages) first.
     */
    @Override
    public CompletableFuture<List<FileInfoResponse>> searchFilesAdvanced(
            String type, Integer limit, String offset, String sort,
            Long minDate, Long maxDate, Long minSize, Long maxSize,
            Long chatId, String filenameContains) {
        int requestedLimit = limit != null ? limit : 50;
        int retrievalLimit = Math.max(requestedLimit * 4, 100);

        CompletableFuture<List<FileInfoResponse>> ownFilesFuture = getRecentFilesFromOwnChat(Math.max(requestedLimit, 50));
        CompletableFuture<List<FileInfoResponse>> searchFilesFuture = searchFilesByType(type, retrievalLimit, offset);

        return CompletableFuture.allOf(ownFilesFuture, searchFilesFuture).thenApply(v -> {
            Set<Long> seenFileIds = new HashSet<>();
            List<FileInfoResponse> combined = new ArrayList<>();

            mergeUniqueFiles(seenFileIds, combined, ownFilesFuture.join());
            mergeUniqueFiles(seenFileIds, combined, searchFilesFuture.join());

            return filterAndSortFiles(combined, new AdvancedSearchOptions(
                sort, requestedLimit, minDate, maxDate, minSize, maxSize, chatId, filenameContains
            ));
        }).exceptionally(ex -> {
            logger.error("[SearchAdvanced] Error: {}", ex.getMessage());
            return List.of();
        });
    }

    private void mergeUniqueFiles(Set<Long> seenFileIds, List<FileInfoResponse> combined, List<FileInfoResponse> files) {
        for (FileInfoResponse file : files) {
            if (seenFileIds.add(file.fileId())) {
                combined.add(file);
                fileMetadataCache.put(file.fileId(), file);
            }
        }
    }

    private List<FileInfoResponse> filterAndSortFiles(List<FileInfoResponse> files, AdvancedSearchOptions options) {
        return files.stream()
            .filter(file -> options.minDate() == null || file.date() >= options.minDate())
            .filter(file -> options.maxDate() == null || file.date() <= options.maxDate())
            .filter(file -> options.minSize() == null || file.fileSize() >= options.minSize())
            .filter(file -> options.maxSize() == null || file.fileSize() <= options.maxSize())
            .filter(file -> options.chatId() == null || file.chatId() == options.chatId())
            .filter(file -> fileNameMatches(file, options.filenameContains()))
            .sorted(getComparator(options.sort()))
            .limit(options.limit())
            .toList();
    }

    private boolean fileNameMatches(FileInfoResponse file, String filenameContains) {
        return filenameContains == null ||
            (file.fileName() != null && file.fileName().toLowerCase().contains(filenameContains.toLowerCase()));
    }

    private Comparator<FileInfoResponse> getComparator(String sort) {
        if (sort == null) return Comparator.comparingLong(FileInfoResponse::date).reversed();

        return switch (sort.toLowerCase()) {
            case "date_asc" -> Comparator.comparingLong(FileInfoResponse::date);
            case "date_desc" -> Comparator.comparingLong(FileInfoResponse::date).reversed();
            case "size_asc" -> Comparator.comparingLong(FileInfoResponse::fileSize);
            case "size_desc" -> Comparator.comparingLong(FileInfoResponse::fileSize).reversed();
            case "name_asc" -> Comparator.comparing(FileInfoResponse::fileName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "name_desc" -> Comparator.comparing(FileInfoResponse::fileName, Comparator.nullsLast(String::compareToIgnoreCase)).reversed();
            default -> Comparator.comparingLong(FileInfoResponse::date).reversed();
        };
    }

    /**
     * Streams a file as a Resource.
     * If the file is not downloaded locally, it downloads it from Telegram first.
     */
    @Override
    public CompletableFuture<Resource> streamFile(long fileId) {
        return getFileInfo(fileId).thenCompose(fileInfo -> {
            // If it is already downloaded, return it directly
            if (fileInfo.isDownloaded() && fileInfo.localPath() != null) {
                File file = new File(fileInfo.localPath());
                if (file.exists()) {
                    return CompletableFuture.completedFuture(new FileSystemResource(file));
                }
            }

            // If it is not downloaded, start the download and wait
            logger.info("[Stream] File not downloaded locally, starting download from Telegram...");
            return downloadAndWait(fileId, ServiceDefaults.STREAM_DOWNLOAD_TIMEOUT_MS); // Wait up to 60 seconds
        });
    }

    /**
     * Starts the download through the seam and polls its state until the
     * local copy is complete or the deadline expires.
     */
    private CompletableFuture<Resource> downloadAndWait(long fileId, long timeoutMs) {
        CompletableFuture<Resource> future = new CompletableFuture<>();
        long deadline = System.currentTimeMillis() + timeoutMs;

        awaitUploadRelease(fileId)
            .thenCompose(_v -> telegramService.startDownload(fileId))
            .whenComplete((state, error) -> {
                if (error != null) {
                    future.completeExceptionally(error);
                    return;
                }
                pollUntilDownloaded(fileId, deadline, future);
            });

        return future;
    }

    /**
     * Waits for a still-uploading file's staged source to be released; a
     * future already complete when no staged source is tracked.
     */
    private CompletableFuture<Void> awaitUploadRelease(long fileId) {
        return telegramService.isUploadTracked(fileId)
            ? telegramService.waitForUploadRelease(fileId)
            : CompletableFuture.completedFuture(null);
    }

    private void pollUntilDownloaded(long fileId, long deadline, CompletableFuture<Resource> future) {
        telegramService.getFileState(fileId).whenComplete((state, error) -> {
            if (future.isDone()) {
                return;
            }
            if (error == null && state.downloaded() && state.localPath() != null) {
                File downloadedFile = new File(state.localPath());
                if (downloadedFile.exists()) {
                    future.complete(new FileSystemResource(downloadedFile));
                    return;
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                future.completeExceptionally(new IllegalStateException("Could not download the file"));
                return;
            }
            CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS)
                .execute(() -> pollUntilDownloaded(fileId, deadline, future));
        });
    }

    /**
     * Streaming information.
     */
    @Override
    public CompletableFuture<FileStreamResponse> getStreamInfo(long fileId) {
        return getFileInfo(fileId).thenApply(fileInfo ->
            new FileStreamResponse(
                fileInfo.fileId(),
                fileInfo.fileName(),
                fileInfo.mimeType(),
                fileInfo.fileSize(),
                "/api/telegram/files/" + fileId + "/content",
                fileInfo.isDownloaded()
            )
        );
    }

    /**
     * Creates a batch download job.
     */
    @Override
    public DownloadJob createBatchDownloadJob(List<Long> fileIds) {
        String jobId = UUID.randomUUID().toString();
        DownloadJob job = new DownloadJob(
            jobId,
            DownloadJob.STATUS_PENDING,
            fileIds,
            0,
            fileIds.size(),
            0,
            Instant.now(),
            null,
            null
        );
        batchJobs.put(jobId, job);

        // Start downloads in the background
        CompletableFuture.runAsync(() -> {
            updateJobStatus(jobId, DownloadJob.STATUS_IN_PROGRESS);

            for (Long fileId : fileIds) {
                try {
                    downloadFile(fileId).join();
                    updateBatchProgress(jobId, fileIds, null);
                } catch (Exception ex) {
                    updateBatchProgress(jobId, fileIds, ex.getMessage());
                }
            }
        });

        return job;
    }

    private void updateJobStatus(String jobId, String status) {
        batchJobs.computeIfPresent(jobId, (id, job) -> new DownloadJob(
                jobId, status, job.fileIds(), job.completedCount(),
                job.totalCount(), job.progressPercentage(), job.createdAt(),
                job.completedAt(), job.errorMessage()
            ));
    }

    private void updateBatchProgress(String jobId, List<Long> fileIds, String errorMessage) {
        batchJobs.computeIfPresent(jobId, (id, current) -> {
            int completed = current.completedCount() + 1;
            int progress = (completed * 100) / current.totalCount();
            return new DownloadJob(
                jobId,
                completed == current.totalCount() ? DownloadJob.STATUS_COMPLETED : DownloadJob.STATUS_IN_PROGRESS,
                fileIds,
                completed,
                current.totalCount(),
                progress,
                current.createdAt(),
                completed == current.totalCount() ? Instant.now() : null,
                errorMessage != null ? errorMessage : current.errorMessage()
            );
        });
    }

    /**
     * Gets the status of a batch job.
     */
    @Override
    public DownloadJob getBatchJobStatus(String jobId) {
        return batchJobs.get(jobId);
    }

    /**
     * File statistics.
     */
    @Override
    @Cacheable(value = "stats", unless = "#result == null")
    public CompletableFuture<FileStatsResponse> getFileStats() {
        return searchFilesByType(FileTypeConstants.ALL, ServiceDefaults.DEFAULT_STATS_FILE_LIMIT, "").thenApply(files -> {
            long totalFiles = files.size();
            long totalSize = files.stream().mapToLong(FileInfoResponse::fileSize).sum();
            long downloadedCount = files.stream().filter(FileInfoResponse::isDownloaded).count();
            long downloadedSize = files.stream()
                .filter(FileInfoResponse::isDownloaded)
                .mapToLong(FileInfoResponse::fileSize)
                .sum();

            Map<String, Long> byType = files.stream()
                .collect(Collectors.groupingBy(FileInfoResponse::type, Collectors.counting()));

            Map<Long, Long> byChat = files.stream()
                .collect(Collectors.groupingBy(FileInfoResponse::chatId, Collectors.counting()));

            return new FileStatsResponse(
                totalFiles,
                totalSize,
                byType,
                byChat,
                downloadedCount,
                totalFiles - downloadedCount,
                downloadedSize
            );
        }).exceptionally(ex -> new FileStatsResponse(0, 0, Map.of(), Map.of(), 0, 0, 0));
    }

    /**
     * File export.
     */
    @Override
    public CompletableFuture<FileExportResponse> exportFiles(String format, String type) {
        String effectiveType = type != null ? type : FileTypeConstants.ALL;
        return searchFilesByType(effectiveType, ServiceDefaults.DEFAULT_EXPORT_FILE_LIMIT, "").thenApply(files ->
            new FileExportResponse(
                format != null ? format : "json",
                files.size(),
                files,
                System.currentTimeMillis()
            )
        ).exceptionally(ex -> new FileExportResponse(format != null ? format : "json", 0, List.of(), System.currentTimeMillis()));
    }

    /**
     * Gets the latest files from the user's own chat (Saved Messages).
     * This allows seeing recently uploaded files that do not yet appear in global search.
     */
    @Override
    public CompletableFuture<List<FileInfoResponse>> getRecentFilesFromOwnChat(int limit) {
        return getOwnChatId()
            .thenCompose(chatId -> telegramService.getFileMessages(chatId, 0, limit)
                .exceptionally(_ex -> List.of()))
            .thenApply(files -> {
                List<FileInfoResponse> mapped = files.stream().map(this::toFileInfo).toList();
                logger.info("[RecentFiles] Retrieved {} files from own chat", mapped.size());
                return mapped;
            })
            .thenCompose(this::enrichVisibleFiles);
    }

    private CompletableFuture<List<FileInfoResponse>> enrichVisibleFiles(List<FileInfoResponse> files) {
        return refreshDownloadStatuses(files)
            .thenCompose(metadataIndexService::enrichAll)
            .thenApply(enriched -> enriched.stream().filter(file -> !file.trashed()).toList());
    }

    /**
     * Gets the current user's chat ID (Saved Messages).
     */
    @Override
    public CompletableFuture<Long> getOwnChatId() {
        return telegramService.getOwnChatId();
    }

    /**
     * Uploads a file to Telegram as a document.
     * Requires a target chatId to be specified.
     */
    @Override
    public CompletableFuture<UploadResponse> uploadFile(java.io.File file, long chatId, String caption) {
        return uploadFile(file, chatId, caption, "/", List.of(), "telegram-upload", false);
    }

    /**
     * Detects whether the file is an image so the correct type can be used.
     */
    @Override
    public boolean isImageFile(java.io.File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(MediaConstants.EXTENSION_JPG) || name.endsWith(MediaConstants.EXTENSION_JPEG) ||
               name.endsWith(MediaConstants.EXTENSION_PNG) || name.endsWith(MediaConstants.EXTENSION_GIF) ||
               name.endsWith(MediaConstants.EXTENSION_WEBP) || name.endsWith(MediaConstants.EXTENSION_BMP);
    }

    @Override
    public CompletableFuture<UploadResponse> uploadPhoto(java.io.File file, long chatId, String caption) {
        return uploadPhoto(file, chatId, caption, "/", List.of(), "telegram-upload", false);
    }

    @Override
    public CompletableFuture<UploadResponse> uploadFile(
            File file,
            long chatId,
            String caption,
            String virtualPath,
            List<String> tags,
            String origin,
            boolean archived) {
        return uploadManaged(file, chatId, caption, virtualPath, tags, false);
    }

    @Override
    public CompletableFuture<UploadResponse> uploadPhoto(
            File file,
            long chatId,
            String caption,
            String virtualPath,
            List<String> tags,
            String origin,
            boolean archived) {
        return uploadManaged(file, chatId, caption, virtualPath, tags, true);
    }

    /**
     * Uploads through the seam, which registers the staged source for
     * tracking and returns the created file message in domain types.
     */
    private CompletableFuture<UploadResponse> uploadManaged(
            File file,
            long chatId,
            String caption,
            String virtualPath,
            List<String> tags,
            boolean photoMode) {
        if (file == null || !file.exists() || !file.canRead()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("File does not exist or is not readable"));
        }

        String checksum = calculateSha256(file);
        List<String> normalizedTags = tags != null ? tags : List.of();
        String normalizedPath = virtualPath != null && !virtualPath.isBlank() ? virtualPath : "/";

        CompletableFuture<TelegramFileMessage> sendFuture = photoMode
            ? telegramService.sendPhoto(chatId, file.getAbsolutePath(), caption)
            : telegramService.sendDocument(chatId, file.getAbsolutePath(), caption);

        return sendFuture.thenApply(uploaded -> new UploadResponse(
            uploaded.messageId(),
            uploaded.chatId(),
            UploadResponse.STATUS_COMPLETED,
            uploaded.fileName() != null && !uploaded.fileName().isBlank() ? uploaded.fileName() : file.getName(),
            uploaded.fileSize() > 0 ? uploaded.fileSize() : file.length(),
            caption,
            photoMode ? "Photo uploaded successfully" : "File uploaded successfully",
            false,
            "telegram-" + uploaded.fileId(),
            1,
            checksum,
            normalizedPath,
            normalizedTags
        ));
    }

    private String calculateSha256(File file) {
        try (var inputStream = java.nio.file.Files.newInputStream(file.toPath())) {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            StringBuilder builder = new StringBuilder();
            for (byte value : digest.digest()) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not calculate the SHA-256 checksum", e);
        }
    }

    /**
     * Deletes a Telegram message.
     *
     * @param messageId ID of the message to delete
     * @param chatId ID of the chat containing the message
     * @param permanent if true, deletes for everyone (revoke); if false, deletes only locally
     * @return CompletableFuture with the deletion response
     */
    @Override
    public CompletableFuture<DeleteMessageResponse> deleteMessage(long messageId, long chatId, boolean permanent) {
        return resolveFileIdByMessage(chatId, messageId)
            .thenCompose(resolvedFileId -> telegramService
                .deleteMessages(chatId, List.of(messageId), permanent)
                .<DeleteMessageResponse>thenApply(_ok -> {
                    if (resolvedFileId != null && resolvedFileId > 0) {
                        clearLocalDownloadStateForFileId(resolvedFileId);
                    }
                    clearLocalDownloadStateForMessage(messageId);
                    invalidateCachedMessage(messageId);
                    return buildDeleteMessageResponse(messageId, chatId, permanent, true, "Message deleted");
                })
                .exceptionally(ex -> buildDeleteMessageResponse(
                    messageId, chatId, permanent, false, "Error deleting message: " + causeMessage(ex))))
            .exceptionally(ex -> buildDeleteMessageResponse(
                messageId, chatId, permanent, false, "Error resolving file to delete: " + causeMessage(ex)));
    }

    /**
     * Deletes multiple Telegram messages in batch.
     * Continues with the remaining files if one fails.
     *
     * @param request batch deletion request
     * @return CompletableFuture with the batch deletion response
     */
    @Override
    public CompletableFuture<BulkDeleteResponse> bulkDeleteMessages(BulkDeleteRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            return CompletableFuture.completedFuture(new BulkDeleteResponse(0, 0, 0, List.of()));
        }

        CompletableFuture<BulkDeleteResponse> future = new CompletableFuture<>();
        List<BulkDeleteResponse.DeleteResult> results = new ArrayList<>();
        int[] successCount = {0};
        int[] failedCount = {0};

        // Process each item sequentially to avoid overloading the API
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (BulkDeleteRequest.DeleteItem item : request.items()) {
            chain = chain.thenCompose(v -> {
                CompletableFuture<Void> itemFuture = new CompletableFuture<>();

                try {
                    deleteMessage(item.messageId(), item.chatId(), request.permanent())
                        .thenAccept(response -> {
                            boolean isSuccess = DeleteMessageResponse.STATUS_SUCCESS.equals(response.status());
                            if (isSuccess) {
                                successCount[0]++;
                            } else {
                                failedCount[0]++;
                            }
                            results.add(new BulkDeleteResponse.DeleteResult(
                                item.fileId(),
                                item.fileName(),
                                isSuccess,
                                response.message()
                            ));
                            itemFuture.complete(null);
                        })
                        .exceptionally(ex -> {
                            failedCount[0]++;
                            results.add(new BulkDeleteResponse.DeleteResult(
                                item.fileId(),
                                item.fileName(),
                                false,
                                "Error: " + ex.getMessage()
                            ));
                            itemFuture.complete(null);
                            return null;
                        });
                } catch (Exception e) {
                    failedCount[0]++;
                    results.add(new BulkDeleteResponse.DeleteResult(
                        item.fileId(),
                        item.fileName(),
                        false,
                        "Unexpected error: " + e.getMessage()
                    ));
                    itemFuture.complete(null);
                }

                return itemFuture;
            });
        }

        chain.thenRun(() ->
            future.complete(new BulkDeleteResponse(
                request.items().size(),
                successCount[0],
                failedCount[0],
                results
            ))
        ).exceptionally(ex -> {
            future.complete(new BulkDeleteResponse(
                request.items().size(),
                successCount[0],
                failedCount[0],
                results
            ));
            return null;
        });

        return future;
    }

    @Override
    public CompletableFuture<FileActionResponse> restoreFile(long fileId) {
        return CompletableFuture.completedFuture(new FileActionResponse(
            fileId,
            FileActionResponse.STATUS_FAILED,
            "Logical trash is disabled",
            "/",
            false,
            false,
            0
        ));
    }

    @Override
    public CompletableFuture<FileActionResponse> moveFile(long fileId, String virtualPath) {
        return CompletableFuture.completedFuture(new FileActionResponse(
            fileId,
            FileActionResponse.STATUS_FAILED,
            "Logical move is disabled",
            "/",
            false,
            false,
            0
        ));
    }

    @Override
    public CompletableFuture<FileActionResponse> archiveFile(long fileId, boolean archived) {
        return CompletableFuture.completedFuture(new FileActionResponse(
            fileId,
            FileActionResponse.STATUS_FAILED,
            "Logical archive is disabled",
            "/",
            false,
            false,
            0
        ));
    }

    @Override
    public CompletableFuture<List<FileInfoResponse>> listTrash() {
        return CompletableFuture.completedFuture(List.of());
    }

    /**
     * Gets the thumbnail resource for a file.
     * It first checks the metadata cache for thumbnailPath.
     * If no thumbnail exists but it is a photo/video, it tries to serve the full content.
     *
     * @param fileId file ID
     * @return CompletableFuture with the thumbnail ResponseEntity<Resource>
     */
    @Override
    public CompletableFuture<ResponseEntity<Resource>> getThumbnailResource(long fileId) {
        CompletableFuture<ResponseEntity<Resource>> future = new CompletableFuture<>();

        FileInfoResponse cached = fileMetadataCache.getIfPresent(fileId);
        ResponseEntity<Resource> cachedThumbnailResponse = resolveThumbnailFileResponse(cached);
        if (cachedThumbnailResponse != null) {
            future.complete(cachedThumbnailResponse);
            return future;
        }

        getFileInfo(fileId).thenAccept(fileInfo -> {
            ResponseEntity<Resource> thumbnailResponse = resolveThumbnailFileResponse(fileInfo);
            if (thumbnailResponse != null) {
                future.complete(thumbnailResponse);
                return;
            }

            ResponseEntity<Resource> fullContentResponse = resolveFullContentThumbnailFallback(fileInfo);
            if (fullContentResponse != null) {
                future.complete(fullContentResponse);
                return;
            }

            future.complete(ResponseEntity.notFound().build());
        }).exceptionally(ex -> {
            future.complete(ResponseEntity.notFound().build());
            return null;
        });

        return future;
    }

    private void invalidateCachedMessage(long messageId) {
        fileMetadataCache.asMap().values().stream()
            .filter(file -> file.messageId() == messageId)
            .findFirst()
            .ifPresent(file -> fileMetadataCache.invalidate(file.fileId()));
    }

    private void clearLocalDownloadStateForMessage(long messageId) {
        fileMetadataCache.asMap().values().stream()
            .filter(file -> file.messageId() == messageId)
            .forEach(file -> clearLocalDownloadStateForFileId(file.fileId()));
    }

    private void clearLocalDownloadStateForFileId(long fileId) {
        FileInfoResponse cached = fileMetadataCache.getIfPresent(fileId);
        if (cached != null) {
            String localPath = cached.localPath();
            if (localPath != null && !localPath.isBlank()) {
                try {
                    java.io.File diskFile = new java.io.File(localPath);
                    if (diskFile.exists()) {
                        diskFile.delete();
                    }
                } catch (Exception ignored) {
                    // Best effort cleanup.
                }
            }
            fileMetadataCache.invalidate(fileId);
        }

        // Best effort TDLib cache cleanup through the seam.
        telegramService.deleteLocalFile(fileId).exceptionally(_ex -> null);
    }

    private CompletableFuture<Long> resolveFileIdByMessage(long chatId, long messageId) {
        Long cachedFileId = fileMetadataCache.asMap().values().stream()
            .filter(file -> file.messageId() == messageId)
            .map(FileInfoResponse::fileId)
            .findFirst()
            .orElse(null);
        if (cachedFileId != null) {
            return CompletableFuture.completedFuture(cachedFileId);
        }

        return telegramService.getFileMessage(chatId, messageId)
            .thenApply(fileMessage -> fileMessage != null ? fileMessage.fileId() : null)
            .exceptionally(_ex -> null);
    }

    private DeleteMessageResponse buildDeleteMessageResponse(
            long messageId,
            long chatId,
            boolean permanent,
            boolean successful,
            String message) {
        return new DeleteMessageResponse(
            messageId,
            chatId,
            successful ? DeleteMessageResponse.STATUS_SUCCESS : DeleteMessageResponse.STATUS_FAILED,
            permanent ? DeleteMessageResponse.DELETION_TYPE_PERMANENT : DeleteMessageResponse.DELETION_TYPE_LOCAL,
            successful ? buildDeleteSuccessMessage(permanent) : message
        );
    }

    private String buildDeleteSuccessMessage(boolean permanent) {
        return permanent ? "Message deleted permanently" : "Message deleted locally";
    }

    private static String causeMessage(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null
            ? error.getCause().getMessage()
            : error.getMessage();
    }

    private ResponseEntity<Resource> resolveThumbnailFileResponse(FileInfoResponse fileInfo) {
        if (fileInfo == null || fileInfo.thumbnailPath() == null || fileInfo.thumbnailPath().isBlank()) {
            return null;
        }
        java.io.File thumbnailFile = new java.io.File(fileInfo.thumbnailPath());
        if (!thumbnailFile.exists()) {
            return null;
        }
        return buildResourceResponse(thumbnailFile, org.springframework.http.MediaType.IMAGE_JPEG);
    }

    private ResponseEntity<Resource> resolveFullContentThumbnailFallback(FileInfoResponse fileInfo) {
        String type = fileInfo.type();
        if (!"PHOTO".equals(type) && !"VIDEO".equals(type)) {
            return null;
        }
        if (!fileInfo.isDownloaded() || fileInfo.localPath() == null) {
            return null;
        }
        java.io.File fullFile = new java.io.File(fileInfo.localPath());
        if (!fullFile.exists()) {
            return null;
        }
        return buildResourceResponse(fullFile, resolveMediaType(fileInfo.mimeType()));
    }

    private ResponseEntity<Resource> buildResourceResponse(
            java.io.File file,
            org.springframework.http.MediaType mediaType) {
        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(CACHE_CONTROL_HEADER, CACHE_CONTROL_PUBLIC_MAX_AGE)
            .body(new FileSystemResource(file));
    }

    private org.springframework.http.MediaType resolveMediaType(String mimeType) {
        String resolvedMimeType = mimeType != null ? mimeType : MediaConstants.MIME_IMAGE_JPEG;
        try {
            return org.springframework.http.MediaType.parseMediaType(resolvedMimeType);
        } catch (Exception _) {
            return org.springframework.http.MediaType.IMAGE_JPEG;
        }
    }
}
