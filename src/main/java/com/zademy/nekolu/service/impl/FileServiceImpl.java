/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.service.impl;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.github.benmanes.caffeine.cache.Cache;
import com.zademy.nekolu.constants.FileTypeConstants;
import com.zademy.nekolu.constants.MediaConstants;
import com.zademy.nekolu.constants.ServiceDefaults;
import com.zademy.nekolu.dto.BulkDeleteRequest;
import com.zademy.nekolu.dto.BulkDeleteResponse;
import com.zademy.nekolu.dto.DeleteMessageResponse;
import com.zademy.nekolu.dto.DownloadJob;
import com.zademy.nekolu.dto.DownloadProgress;
import com.zademy.nekolu.dto.DownloadResponse;
import com.zademy.nekolu.dto.FileActionResponse;
import com.zademy.nekolu.dto.FileExportResponse;
import com.zademy.nekolu.dto.FileInfoResponse;
import com.zademy.nekolu.dto.FileStatsResponse;
import com.zademy.nekolu.dto.FileStreamResponse;
import com.zademy.nekolu.dto.UploadResponse;
import com.zademy.nekolu.service.FileService;
import com.zademy.nekolu.service.MetadataIndexService;
import com.zademy.nekolu.service.TelegramService;

/**
 * Implementation of the Telegram file management service.
 */
@Service
public class FileServiceImpl implements FileService {
    private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);
    private static final String TELEGRAM_CLIENT_NOT_INITIALIZED = "Telegram client not initialized";
    private static final String TELEGRAM_AUTH_REQUIRED = "Unauthorized. Telegram requires authentication.";
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
    private final Client client;
    private final Cache<Long, FileInfoResponse> fileMetadataCache;

    public FileServiceImpl(
        TelegramService telegramService,
        MetadataIndexService metadataIndexService,
        @Qualifier("fileInfoNativeCache") Cache<Long, FileInfoResponse> fileMetadataCache
    ) {
        this.telegramService = telegramService;
        this.metadataIndexService = metadataIndexService;
        this.client = telegramService.getClient();
        this.fileMetadataCache = fileMetadataCache;
    }

    private static final List<String> ALL_FILE_TYPES = FileTypeConstants.SEARCHABLE_TYPES;

    /**
     * Searches files inside a specific chat/folder.
     * Uses SearchChatMessages to search INSIDE the chat, not globally.
     * This allows finding files in private channels (folders).
     *
     * @param chatId chat/folder ID to search in
     * @param type file type (photo, video, audio, document, voice, video_note, all)
     * @param limit result limit
     * @return list of matching files
     */
    @Override
    public CompletableFuture<List<FileInfoResponse>> searchFilesInChat(long chatId, String type, int limit) {
        if (client == null) {
            return CompletableFuture.completedFuture(List.of());
        }

        if (!telegramService.isAuthorized()) {
            return CompletableFuture.completedFuture(List.of());
        }
        CompletableFuture<List<FileInfoResponse>> future = new CompletableFuture<>();

        TdApi.GetChatHistory getHistory = new TdApi.GetChatHistory();
        getHistory.chatId = chatId;
        getHistory.fromMessageId = 0;
        getHistory.offset = 0;
        getHistory.limit = Math.max(limit * 4, 100);
        getHistory.onlyLocal = false;

        client.send(getHistory, result -> {
            switch (result) {
                case TdApi.Messages messages -> {
                    List<FileInfoResponse> files = extractFilesFromMessages(messages.messages).stream()
                        .filter(file -> matchesRequestedType(file, type))
                        .sorted(Comparator.comparingLong(FileInfoResponse::date).reversed())
                        .limit(limit)
                        .toList();
                    future.complete(files);
                }
                case TdApi.Error error -> {
                    logger.error("[SearchInChat] Error retrieving history for chat {}: {}", chatId, error.message);
                    future.complete(List.of());
                }
                default -> future.complete(List.of());
            }
        });

        return future.thenCompose(this::enrichVisibleFiles);
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
        if (client == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(TELEGRAM_CLIENT_NOT_INITIALIZED));
        }

        if (!telegramService.isAuthorized()) {
            return CompletableFuture.failedFuture(new IllegalStateException(TELEGRAM_AUTH_REQUIRED));
        }

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
        CompletableFuture<List<FileInfoResponse>> future = new CompletableFuture<>();

        TdApi.SearchMessagesFilter filter = createFilterForType(type);

        TdApi.SearchMessages search = new TdApi.SearchMessages();
        search.chatList = null;
        search.query = "";
        search.offset = offset != null ? offset : "";
        search.limit = limit;
        search.filter = filter;
        search.minDate = 0;
        search.maxDate = 0;

        client.send(search, result -> {
            if (result instanceof TdApi.FoundMessages foundMessages) {
                List<FileInfoResponse> files = extractFilesFromMessages(foundMessages.messages);
                future.complete(files);
            } else if (result instanceof TdApi.Error) {
                future.complete(List.of());
            }
        });

        return future;
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

        CompletableFuture<FileInfoResponse> future = new CompletableFuture<>();

        if (client == null) {
            future.completeExceptionally(new IllegalStateException(TELEGRAM_CLIENT_NOT_INITIALIZED));
            return future;
        }

        TdApi.GetFile getFile = new TdApi.GetFile();
        getFile.fileId = (int) fileId;

        client.send(getFile, result -> {
            if (result instanceof TdApi.File file) {
                FileInfoResponse response = mapTdFileToResponse(file);
                future.complete(response);
            } else if (result instanceof TdApi.Error error) {
                future.completeExceptionally(new RuntimeException("Error getting file: " + error.message));
            }
        });

        return future.thenCompose(metadataIndexService::enrich);
    }

    private CompletableFuture<FileInfoResponse> refreshDownloadStatus(FileInfoResponse cached) {
        CompletableFuture<FileInfoResponse> future = new CompletableFuture<>();

        if (client == null) {
            future.complete(cached);
            return future;
        }

        TdApi.GetFile getFile = new TdApi.GetFile();
        getFile.fileId = (int) cached.fileId();

        client.send(getFile, result -> {
            if (result instanceof TdApi.File file) {
                boolean downloaded = isFileActuallyDownloaded(file);
                String localPath = file.local != null ? file.local.path : null;
                FileInfoResponse updated = new FileInfoResponse(
                    cached.messageId(), cached.chatId(), cached.fileId(),
                    cached.fileName(), file.size > 0 ? file.size : cached.fileSize(),
                    cached.mimeType(), cached.type(),
                    cached.width(), cached.height(), cached.duration(),
                    cached.thumbnailPath(), cached.date(),
                    downloaded,
                    localPath != null && !localPath.isBlank() ? localPath : cached.localPath()
                );
                fileMetadataCache.put(cached.fileId(), updated);
                future.complete(updated);
            } else {
                future.complete(cached);
            }
        });

        return future;
    }

    private CompletableFuture<List<FileInfoResponse>> refreshDownloadStatuses(List<FileInfoResponse> files) {
        if (files == null || files.isEmpty() || client == null) {
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
     * Starts a file download and returns immediately with PENDING status.
     * The client should query GET /{fileId} to check progress.
     */
    @Override
    public CompletableFuture<DownloadResponse> downloadFile(long fileId) {
        CompletableFuture<DownloadResponse> future = new CompletableFuture<>();

        if (client == null) {
            future.completeExceptionally(new IllegalStateException(TELEGRAM_CLIENT_NOT_INITIALIZED));
            return future;
        }

        // Check whether it is already downloaded
        getFileInfo(fileId).thenAccept(fileInfo -> {
            if (fileInfo.isDownloaded() && fileInfo.localPath() != null) {
                future.complete(new DownloadResponse(
                    fileId,
                    DownloadResponse.STATUS_COMPLETED,
                    fileInfo.localPath(),
                    100,
                    "File already downloaded"
                ));
                return;
            }

            Runnable startDownload = () -> {
                TdApi.DownloadFile download = new TdApi.DownloadFile();
                download.fileId = (int) fileId;
                download.priority = 1;
                download.offset = 0;
                download.limit = 0;
                download.synchronous = false;

                client.send(download, result -> {
                    if (result instanceof TdApi.Error error) {
                        logger.error("[Download] Error starting download for {}: {}", fileId, error.message);
                    }
                });
            };

            if (telegramService.isUploadTracked((int) fileId)) {
                telegramService.waitForUploadRelease((int) fileId)
                    .thenRun(startDownload)
                    .exceptionally(ex -> {
                        logger.error("[Download] Error waiting for upload release for {}: {}", fileId, ex.getMessage());
                        return null;
                    });
            } else {
                startDownload.run();
            }

            // Return immediately with PENDING
            future.complete(new DownloadResponse(
                fileId,
                DownloadResponse.STATUS_PENDING,
                null,
                0,
                "Download started in background. Check GET /{fileId} for progress."
            ));

        }).exceptionally(ex -> {
            future.complete(new DownloadResponse(
                fileId,
                DownloadResponse.STATUS_FAILED,
                null,
                0,
                ex.getMessage()
            ));
            return null;
        });

        return future;
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
     * Creates the search filter for the given type.
     */
    private TdApi.SearchMessagesFilter createFilterForType(String type) {
        if (type == null) return null;

        return switch (type.toLowerCase()) {
            case FileTypeConstants.PHOTO -> new TdApi.SearchMessagesFilterPhoto();
            case FileTypeConstants.VIDEO -> new TdApi.SearchMessagesFilterVideo();
            case FileTypeConstants.AUDIO -> new TdApi.SearchMessagesFilterAudio();
            case FileTypeConstants.DOCUMENT -> new TdApi.SearchMessagesFilterDocument();
            case FileTypeConstants.VOICE -> new TdApi.SearchMessagesFilterVoiceNote();
            case FileTypeConstants.VIDEO_NOTE -> new TdApi.SearchMessagesFilterVideoNote();
            default -> null; // No filter = all
        };
    }

    /**
     * Extracts file information from messages.
     */
    private List<FileInfoResponse> extractFilesFromMessages(TdApi.Message[] messages) {
        List<FileInfoResponse> files = new ArrayList<>();

        for (TdApi.Message message : messages) {
            FileInfoResponse fileInfo = extractFileFromMessage(message);
            if (fileInfo != null) {
                files.add(fileInfo);
                fileMetadataCache.put(fileInfo.fileId(), fileInfo);
            }
        }

        return files;
    }

    /**
     * Extracts file information from a message based on its type.
     */
    private FileInfoResponse extractFileFromMessage(TdApi.Message message) {
        if (message.content == null) return null;

        return switch (message.content) {
            case TdApi.MessagePhoto photo -> extractPhotoInfo(message, photo);
            case TdApi.MessageVideo video -> extractVideoInfo(message, video);
            case TdApi.MessageAudio audio -> extractAudioInfo(message, audio);
            case TdApi.MessageDocument doc -> extractDocumentInfo(message, doc);
            case TdApi.MessageVoiceNote voice -> extractVoiceInfo(message, voice);
            case TdApi.MessageVideoNote videoNote -> extractVideoNoteInfo(message, videoNote);
            default -> null;
        };
    }

    private FileInfoResponse extractPhotoInfo(TdApi.Message message, TdApi.MessagePhoto photo) {
        if (photo.photo == null || photo.photo.sizes == null || photo.photo.sizes.length == 0) {
            return null;
        }
        // Use the largest size for the main file
        TdApi.PhotoSize largest = photo.photo.sizes[photo.photo.sizes.length - 1];
        TdApi.File file = largest.photo;

        // Use the smallest size for the thumbnail
        TdApi.PhotoSize smallest = photo.photo.sizes[0];
        String thumbnailPath = extractThumbnailPath(smallest.photo);

        return new FileInfoResponse(
            message.id,
            message.chatId,
            file.id,
            MediaConstants.FILE_PREFIX_PHOTO + file.id + MediaConstants.EXTENSION_JPG,
            file.size,
            MediaConstants.MIME_IMAGE_JPEG,
            FileTypeConstants.PHOTO_KIND,
            largest.width,
            largest.height,
            null,
            thumbnailPath,
            message.date,
            isFileActuallyDownloaded(file),
            file.local != null ? file.local.path : null
        );
    }

    /**
     * Extracts the thumbnail path when it is available locally.
     * It does not force a download and only returns the path if it is already downloaded.
     */
    private String extractThumbnailPath(TdApi.File thumbnailFile) {
        if (thumbnailFile == null || thumbnailFile.local == null) {
            return null;
        }
        if (thumbnailFile.local.isDownloadingCompleted && thumbnailFile.local.path != null
                && !thumbnailFile.local.path.isBlank()) {
            return thumbnailFile.local.path;
        }
        return null;
    }

    private FileInfoResponse extractVideoInfo(TdApi.Message message, TdApi.MessageVideo video) {
        if (video.video == null) return null;
        TdApi.File file = video.video.video;

        // Extract the video thumbnail if present
        String thumbnailPath = null;
        if (video.video.thumbnail != null && video.video.thumbnail.file != null) {
            thumbnailPath = extractThumbnailPath(video.video.thumbnail.file);
        }

        return new FileInfoResponse(
            message.id,
            message.chatId,
            file.id,
            video.video.fileName != null ? video.video.fileName
                : MediaConstants.FILE_PREFIX_VIDEO + file.id + MediaConstants.EXTENSION_MP4,
            file.size,
            video.video.mimeType != null ? video.video.mimeType : MediaConstants.MIME_VIDEO_MP4,
            FileTypeConstants.VIDEO_KIND,
            video.video.width,
            video.video.height,
            video.video.duration,
            thumbnailPath,
            message.date,
            isFileActuallyDownloaded(file),
            file.local != null ? file.local.path : null
        );
    }

    private FileInfoResponse extractAudioInfo(TdApi.Message message, TdApi.MessageAudio audio) {
        if (audio.audio == null) return null;
        TdApi.File file = audio.audio.audio;

        return new FileInfoResponse(
            message.id,
            message.chatId,
            file.id,
            audio.audio.fileName != null ? audio.audio.fileName
                : MediaConstants.FILE_PREFIX_AUDIO + file.id + MediaConstants.EXTENSION_MP3,
            file.size,
            audio.audio.mimeType != null ? audio.audio.mimeType : MediaConstants.MIME_AUDIO_MPEG,
            FileTypeConstants.AUDIO_KIND,
            null,
            null,
            audio.audio.duration,
            null,
            message.date,
            isFileActuallyDownloaded(file),
            file.local != null ? file.local.path : null
        );
    }

    private FileInfoResponse extractDocumentInfo(TdApi.Message message, TdApi.MessageDocument doc) {
        if (doc.document == null) return null;
        TdApi.File file = doc.document.document;

        return new FileInfoResponse(
            message.id,
            message.chatId,
            file.id,
            doc.document.fileName != null ? doc.document.fileName
                : MediaConstants.FILE_PREFIX_DOCUMENT + file.id,
            file.size,
            doc.document.mimeType != null ? doc.document.mimeType : MediaConstants.MIME_APPLICATION_OCTET_STREAM,
            FileTypeConstants.DOCUMENT_KIND,
            null,
            null,
            null,
            null,
            message.date,
            isFileActuallyDownloaded(file),
            file.local != null ? file.local.path : null
        );
    }

    private FileInfoResponse extractVoiceInfo(TdApi.Message message, TdApi.MessageVoiceNote voice) {
        if (voice.voiceNote == null) return null;
        TdApi.File file = voice.voiceNote.voice;

        return new FileInfoResponse(
            message.id,
            message.chatId,
            file.id,
            MediaConstants.FILE_PREFIX_VOICE + file.id + MediaConstants.EXTENSION_OGA,
            file.size,
            MediaConstants.MIME_AUDIO_OGG,
            FileTypeConstants.VOICE_KIND,
            null,
            null,
            voice.voiceNote.duration,
            null,
            message.date,
            isFileActuallyDownloaded(file),
            file.local != null ? file.local.path : null
        );
    }

    private FileInfoResponse extractVideoNoteInfo(TdApi.Message message, TdApi.MessageVideoNote videoNote) {
        if (videoNote.videoNote == null) return null;
        TdApi.File file = videoNote.videoNote.video;

        return new FileInfoResponse(
            message.id,
            message.chatId,
            file.id,
            MediaConstants.FILE_PREFIX_VIDEO_NOTE + file.id + MediaConstants.EXTENSION_MP4,
            file.size,
            MediaConstants.MIME_VIDEO_MP4,
            FileTypeConstants.VIDEO_NOTE_KIND,
            videoNote.videoNote.length,
            videoNote.videoNote.length,
            videoNote.videoNote.duration,
            null,
            message.date,
            isFileActuallyDownloaded(file),
            file.local != null ? file.local.path : null
        );
    }

    private boolean isFileActuallyDownloaded(TdApi.File file) {
        if (telegramService.isUploadTracked(file.id)) {
            return false;
        }
        if (file.local == null || !file.local.isDownloadingCompleted) {
            return false;
        }
        if (file.local.path == null || file.local.path.isBlank()) {
            return false;
        }
        return new java.io.File(file.local.path).exists();
    }

    private FileInfoResponse mapTdFileToResponse(TdApi.File file) {
        String localPath = file.local != null ? file.local.path : null;
        String mimeType = guessMimeTypeFromPath(localPath);
        String type = guessTypeFromMime(mimeType);

        return new FileInfoResponse(
            0,
            0,
            file.id,
            null,
            file.size,
            mimeType,
            type,
            null,
            null,
            null,
            null,
            0,
            isFileActuallyDownloaded(file),
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

    private String guessTypeFromMime(String mimeType) {
        if (mimeType == null) return FileTypeConstants.FILE_KIND;
        if (mimeType.startsWith(MediaConstants.MIME_IMAGE_PREFIX)) return FileTypeConstants.PHOTO_KIND;
        if (mimeType.startsWith(MediaConstants.MIME_VIDEO_PREFIX)) return FileTypeConstants.VIDEO_KIND;
        if (mimeType.startsWith(MediaConstants.MIME_AUDIO_PREFIX)) return FileTypeConstants.AUDIO_KIND;
        return FileTypeConstants.DOCUMENT_KIND;
    }

    // ==================== NEW V2 METHODS ====================

    private final ConcurrentHashMap<String, DownloadJob> batchJobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<SseEmitter>> progressEmitters = new ConcurrentHashMap<>();

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
     * Downloads a file from Telegram and waits for completion.
     */
    private CompletableFuture<Resource> downloadAndWait(long fileId, long timeoutMs) {
        return telegramService.waitForUploadRelease((int) fileId)
            .thenCompose(_ignored -> telegramService.downloadFile((int) fileId))
            .orTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .thenCompose(file -> getFileInfo(fileId))
            .thenApply(updatedInfo -> {
                if (updatedInfo.localPath() != null) {
                    File downloadedFile = new File(updatedInfo.localPath());
                    if (downloadedFile.exists()) {
                        return (Resource) new FileSystemResource(downloadedFile);
                    }
                }
                throw new IllegalStateException("Could not download the file");
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
     * Subscribes to SSE progress updates.
     */
    @Override
    public SseEmitter subscribeToProgress(long fileId) {
        SseEmitter emitter = new SseEmitter(ServiceDefaults.SSE_TIMEOUT_MS);

        progressEmitters.computeIfAbsent(fileId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(fileId, emitter));
        emitter.onTimeout(() -> removeEmitter(fileId, emitter));
        emitter.onError(e -> removeEmitter(fileId, emitter));

        // Send initial status
        try {
            getFileInfo(fileId).thenAccept(fileInfo -> {
                DownloadProgress progress = new DownloadProgress(
                    fileId,
                    fileInfo.isDownloaded() ? DownloadResponse.STATUS_COMPLETED : DownloadResponse.STATUS_PENDING,
                    fileInfo.isDownloaded() ? fileInfo.fileSize() : 0,
                    fileInfo.fileSize(),
                    fileInfo.isDownloaded() ? 100 : 0,
                    0,
                    null,
                    fileInfo.localPath(),
                    System.currentTimeMillis()
                );
                try {
                    emitter.send(progress);
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            });
        } catch (Exception e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    private void removeEmitter(long fileId, SseEmitter emitter) {
        List<SseEmitter> emitters = progressEmitters.get(fileId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
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
     * This allows seeing recently uploaded files that do not yet appear in SearchMessages.
     */
    @Override
    public CompletableFuture<List<FileInfoResponse>> getRecentFilesFromOwnChat(int limit) {
        return getOwnChatId().thenCompose(chatId -> {
            CompletableFuture<List<FileInfoResponse>> future = new CompletableFuture<>();

            TdApi.GetChatHistory getHistory = new TdApi.GetChatHistory();
            getHistory.chatId = chatId;
            getHistory.fromMessageId = 0;
            getHistory.offset = 0;
            getHistory.limit = limit;
            getHistory.onlyLocal = false;

            client.send(getHistory, result -> {
                if (result instanceof TdApi.Messages messages) {
                    List<FileInfoResponse> files = extractFilesFromMessages(messages.messages);
                    logger.info("[RecentFiles] Retrieved {} files from own chat ({})", files.size(), chatId);
                    future.complete(files);
                } else if (result instanceof TdApi.Error error) {
                    logger.error("[RecentFiles] Error: {}", error.message);
                    future.complete(List.of());
                }
            });

            return future;
        }).thenCompose(this::enrichVisibleFiles);
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
    /**
     * Extracts the fileId from a message based on its content type.
     */
    private int extractFileIdFromMessage(TdApi.Message message) {
        if (message.content == null) return 0;

        return switch (message.content) {
            case TdApi.MessageDocument doc when doc.document != null && doc.document.document != null -> doc.document.document.id;
            case TdApi.MessagePhoto photo when photo.photo != null && photo.photo.sizes.length > 0 ->
                photo.photo.sizes[photo.photo.sizes.length - 1].photo.id;
            case TdApi.MessageVideo video when video.video != null && video.video.video != null -> video.video.video.id;
            case TdApi.MessageAudio audio when audio.audio != null && audio.audio.audio != null -> audio.audio.audio.id;
            case TdApi.MessageVoiceNote voice when voice.voiceNote != null && voice.voiceNote.voice != null -> voice.voiceNote.voice.id;
            case TdApi.MessageVideoNote videoNote when videoNote.videoNote != null && videoNote.videoNote.video != null -> videoNote.videoNote.video.id;
            default -> 0;
        };
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

    private CompletableFuture<UploadResponse> uploadManaged(
            File file,
            long chatId,
            String caption,
            String virtualPath,
            List<String> tags,
            boolean photoMode) {
        if (client == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(TELEGRAM_CLIENT_NOT_INITIALIZED));
        }

        if (!telegramService.isAuthorized()) {
            return CompletableFuture.failedFuture(new IllegalStateException(TELEGRAM_AUTH_REQUIRED));
        }

        if (file == null || !file.exists() || !file.canRead()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("File does not exist or is not readable"));
        }

        String checksum = calculateSha256(file);
        List<String> normalizedTags = tags != null ? tags : List.of();
        String normalizedPath = virtualPath != null && !virtualPath.isBlank() ? virtualPath : "/";

        return (photoMode ? sendPhotoToTelegram(file, chatId, caption) : sendDocumentToTelegram(file, chatId, caption))
            .thenApply(baseInfo -> new UploadResponse(
                baseInfo.messageId(),
                baseInfo.chatId(),
                UploadResponse.STATUS_COMPLETED,
                file.getName(),
                file.length(),
                caption,
                photoMode ? "Photo uploaded successfully" : "File uploaded successfully",
                false,
                "telegram-" + baseInfo.fileId(),
                1,
                checksum,
                normalizedPath,
                normalizedTags
            ));
    }

    private CompletableFuture<FileInfoResponse> sendDocumentToTelegram(File file, long chatId, String caption) {
        CompletableFuture<FileInfoResponse> future = new CompletableFuture<>();

        TdApi.InputMessageDocument document = new TdApi.InputMessageDocument();
        document.document = new TdApi.InputFileLocal(file.getAbsolutePath());
        document.thumbnail = null;
        document.caption = caption != null ? new TdApi.FormattedText(caption, null) : null;

        TdApi.SendMessage sendMessage = new TdApi.SendMessage();
        sendMessage.chatId = chatId;
        sendMessage.inputMessageContent = document;

        client.send(sendMessage, result -> handleUploadResult(result, file, future));
        return future;
    }

    private CompletableFuture<FileInfoResponse> sendPhotoToTelegram(File file, long chatId, String caption) {
        CompletableFuture<FileInfoResponse> future = new CompletableFuture<>();

        TdApi.InputMessagePhoto photo = new TdApi.InputMessagePhoto();
        photo.photo = new TdApi.InputFileLocal(file.getAbsolutePath());
        photo.thumbnail = null;
        photo.caption = caption != null ? new TdApi.FormattedText(caption, null) : null;
        photo.hasSpoiler = false;

        TdApi.SendMessage sendMessage = new TdApi.SendMessage();
        sendMessage.chatId = chatId;
        sendMessage.inputMessageContent = photo;

        client.send(sendMessage, result -> handleUploadResult(result, file, future));
        return future;
    }

    private void handleUploadResult(TdApi.Object result, File file, CompletableFuture<FileInfoResponse> future) {
        if (result instanceof TdApi.Message message) {
            int fileId = extractFileIdFromMessage(message);
            if (fileId != 0) {
                telegramService.trackUpload(fileId, file.getAbsolutePath());
            }
            FileInfoResponse extracted = extractFileFromMessage(message);
            if (extracted != null) {
                FileInfoResponse normalized = normalizeUploadedFileInfo(extracted, file);
                fileMetadataCache.put(normalized.fileId(), normalized);
                future.complete(normalized);
                return;
            }

            future.complete(buildFallbackUploadedFileInfo(message, file, fileId));
            return;
        }

        if (result instanceof TdApi.Error error) {
            future.completeExceptionally(new RuntimeException("Telegram error: " + error.message));
            return;
        }

        future.completeExceptionally(new RuntimeException("Unexpected TDLib response"));
    }

    private FileInfoResponse normalizeUploadedFileInfo(FileInfoResponse extracted, File file) {
        long normalizedFileSize = extracted.fileSize() > 0 ? extracted.fileSize() : file.length();
        String normalizedFileName = (extracted.fileName() != null && !extracted.fileName().isBlank())
            ? extracted.fileName()
            : file.getName();

        // Important: "uploaded" does not mean "downloaded locally from Telegram".
        // New uploads must start as pending until the user explicitly downloads them.
        return new FileInfoResponse(
            extracted.messageId(),
            extracted.chatId(),
            extracted.fileId(),
            normalizedFileName,
            normalizedFileSize,
            extracted.mimeType(),
            extracted.type(),
            extracted.width(),
            extracted.height(),
            extracted.duration(),
            extracted.thumbnailPath(),
            extracted.date(),
            false,
            null
        );
    }

    private FileInfoResponse buildFallbackUploadedFileInfo(TdApi.Message message, File file, int fileId) {
        return new FileInfoResponse(
            message.id,
            message.chatId,
            fileId,
            file.getName(),
            file.length(),
            null,
            isImageFile(file) ? FileTypeConstants.PHOTO_KIND : FileTypeConstants.DOCUMENT_KIND,
            null,
            null,
            null,
            null,
            message.date,
            false,
            null
        );
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
        CompletableFuture<DeleteMessageResponse> future = new CompletableFuture<>();

        if (client == null) {
            future.completeExceptionally(new IllegalStateException(TELEGRAM_CLIENT_NOT_INITIALIZED));
            return future;
        }

        if (!telegramService.isAuthorized()) {
            future.completeExceptionally(new IllegalStateException(TELEGRAM_AUTH_REQUIRED));
            return future;
        }

        resolveFileIdByMessage(chatId, messageId).thenAccept(resolvedFileId -> {
            TdApi.DeleteMessages deleteMessages = new TdApi.DeleteMessages();
            deleteMessages.chatId = chatId;
            deleteMessages.messageIds = new long[] { messageId };
            deleteMessages.revoke = permanent;

            client.send(deleteMessages, result -> {
                switch (result) {
                    case TdApi.Ok _ -> {
                        if (resolvedFileId != null && resolvedFileId > 0) {
                            clearLocalDownloadStateForFileId(resolvedFileId);
                        }
                        clearLocalDownloadStateForMessage(messageId);
                        invalidateCachedMessage(messageId);
                        future.complete(buildDeleteMessageResponse(messageId, chatId, permanent, true, "Message deleted"));
                    }
                    case TdApi.Error error -> future.complete(
                        buildDeleteMessageResponse(messageId, chatId, permanent, false, "Error deleting message: " + error.message)
                    );
                    default -> future.complete(
                        buildDeleteMessageResponse(messageId, chatId, permanent, false, "Unexpected TDLib response")
                    );
                }
            });
        }).exceptionally(ex -> {
            future.complete(buildDeleteMessageResponse(
                messageId, chatId, permanent, false, "Error resolving file to delete: " + ex.getMessage()
            ));
            return null;
        });

        return future;
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
        CompletableFuture<BulkDeleteResponse> future = new CompletableFuture<>();

        if (client == null) {
            future.completeExceptionally(new IllegalStateException(TELEGRAM_CLIENT_NOT_INITIALIZED));
            return future;
        }

        if (!telegramService.isAuthorized()) {
            future.completeExceptionally(new IllegalStateException(TELEGRAM_AUTH_REQUIRED));
            return future;
        }

        if (request.items() == null || request.items().isEmpty()) {
            future.complete(new BulkDeleteResponse(0, 0, 0, List.of()));
            return future;
        }

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

        if (client == null) {
            future.complete(ResponseEntity.notFound().build());
            return future;
        }

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

        if (client != null) {
            try {
                TdApi.DeleteFile deleteFile = new TdApi.DeleteFile();
                deleteFile.fileId = (int) fileId;
                client.send(deleteFile, _result -> {
                    // Best effort TDLib cache cleanup.
                });
            } catch (Exception ignored) {
                // Best effort cleanup if TDLib version does not support DeleteFile.
            }
        }
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

        CompletableFuture<Long> future = new CompletableFuture<>();
        TdApi.GetMessage getMessage = new TdApi.GetMessage();
        getMessage.chatId = chatId;
        getMessage.messageId = messageId;
        client.send(getMessage, result -> {
            if (result instanceof TdApi.Message tdMessage) {
                FileInfoResponse extracted = extractFileFromMessage(tdMessage);
                future.complete(extracted != null ? extracted.fileId() : null);
                return;
            }
            future.complete(null);
        });
        return future;
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
