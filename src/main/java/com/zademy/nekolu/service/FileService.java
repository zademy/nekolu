/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.service;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

/**
 * Interface for the Telegram file management service.
 * Provides operations to search, download, upload, and manage files.
 */
public interface FileService {

    /**
     * Searches files inside a specific chat/folder.
     *
     * @param chatId chat/folder ID to search in
     * @param type file type (photo, video, audio, document, voice, video_note, all)
     * @param limit result limit
     * @return list of matching files
     */
    CompletableFuture<List<FileInfoResponse>> searchFilesInChat(long chatId, String type, int limit);

    /**
     * Searches files by type across all chats.
     *
     * @param type file type
     * @param limit result limit
     * @param offset pagination offset
     * @return list of matching files
     */
    CompletableFuture<List<FileInfoResponse>> searchFilesByType(String type, int limit, String offset);

    /**
     * Gets information about a specific file.
     *
     * @param fileId file ID
     * @return file information
     */
    CompletableFuture<FileInfoResponse> getFileInfo(long fileId);

    /**
     * Starts a file download.
     *
     * @param fileId file ID to download
     * @return response with the download status
     */
    CompletableFuture<DownloadResponse> downloadFile(long fileId);

    /**
     * Downloads multiple files.
     *
     * @param fileIds list of file IDs
     * @return list of download responses
     */
    CompletableFuture<List<DownloadResponse>> downloadFiles(List<Long> fileIds);

    /**
     * Performs advanced search with filters, sorting, and pagination.
     *
     * @param type file type
     * @param limit result limit
     * @param offset pagination offset
     * @param sort sorting (date_asc, date_desc, size_asc, size_desc, name_asc, name_desc)
     * @param minDate minimum date (timestamp)
     * @param maxDate maximum date (timestamp)
     * @param minSize minimum size in bytes
     * @param maxSize maximum size in bytes
     * @param chatId specific chat ID
     * @param filenameContains text to search within the file name
     * @return filtered file list
     */
    CompletableFuture<List<FileInfoResponse>> searchFilesAdvanced(
            String type, Integer limit, String offset, String sort,
            Long minDate, Long maxDate, Long minSize, Long maxSize,
            Long chatId, String filenameContains);

    /**
     * Streams a file as a Resource.
     *
     * @param fileId file ID
     * @return file Resource
     */
    CompletableFuture<Resource> streamFile(long fileId);

    /**
     * Gets streaming information for a file.
     *
     * @param fileId file ID
     * @return streaming information
     */
    CompletableFuture<FileStreamResponse> getStreamInfo(long fileId);

    /**
     * Subscribes to download progress through SSE.
     *
     * @param fileId file ID
     * @return SseEmitter for receiving updates
     */
    SseEmitter subscribeToProgress(long fileId);

    /**
     * Creates a batch download job.
     *
     * @param fileIds list of file IDs to download
     * @return created download job
     */
    DownloadJob createBatchDownloadJob(List<Long> fileIds);

    /**
     * Gets the status of a batch job.
     *
     * @param jobId job ID
     * @return job status
     */
    DownloadJob getBatchJobStatus(String jobId);

    /**
     * Gets file statistics.
     *
     * @return file statistics
     */
    CompletableFuture<FileStatsResponse> getFileStats();

    /**
     * Exports files in the requested format.
     *
     * @param format export format (json, csv)
     * @param type file type
     * @return response with exported data
     */
    CompletableFuture<FileExportResponse> exportFiles(String format, String type);

    /**
     * Gets the latest files from the user's own chat (Saved Messages).
     *
     * @param limit result limit
     * @return list of recent files
     */
    CompletableFuture<List<FileInfoResponse>> getRecentFilesFromOwnChat(int limit);

    /**
     * Gets the current user's chat ID (Saved Messages).
     *
     * @return own chat ID
     */
    CompletableFuture<Long> getOwnChatId();

    /**
     * Uploads a file to Telegram as a document.
     *
     * @param file file to upload
     * @param chatId target chat ID
     * @param caption optional caption
     * @return upload response
     */
    CompletableFuture<UploadResponse> uploadFile(File file, long chatId, String caption);

    CompletableFuture<UploadResponse> uploadFile(
            File file,
            long chatId,
            String caption,
            String virtualPath,
            List<String> tags,
            String origin,
            boolean archived);

    /**
     * Detects whether the file is an image.
     *
     * @param file file to check
     * @return true if it is an image
     */
    boolean isImageFile(File file);

    /**
     * Uploads a photo to Telegram.
     *
     * @param file image file
     * @param chatId target chat ID
     * @param caption optional caption
     * @return upload response
     */
    CompletableFuture<UploadResponse> uploadPhoto(File file, long chatId, String caption);

    CompletableFuture<UploadResponse> uploadPhoto(
            File file,
            long chatId,
            String caption,
            String virtualPath,
            List<String> tags,
            String origin,
            boolean archived);

    /**
     * Deletes a Telegram message.
     *
     * @param messageId message ID
     * @param chatId chat ID
     * @param permanent if true, deletes it for everyone
     * @return deletion response
     */
    CompletableFuture<DeleteMessageResponse> deleteMessage(long messageId, long chatId, boolean permanent);

    /**
     * Deletes multiple messages in batch.
     *
     * @param request deletion request
     * @return batch deletion response
     */
    CompletableFuture<BulkDeleteResponse> bulkDeleteMessages(BulkDeleteRequest request);

    CompletableFuture<FileActionResponse> restoreFile(long fileId);

    CompletableFuture<FileActionResponse> moveFile(long fileId, String virtualPath);

    CompletableFuture<FileActionResponse> archiveFile(long fileId, boolean archived);

    CompletableFuture<List<FileInfoResponse>> listTrash();

    /**
     * Gets the thumbnail resource for a file.
     *
     * @param fileId file ID
     * @return ResponseEntity with the thumbnail resource
     */
    CompletableFuture<ResponseEntity<Resource>> getThumbnailResource(long fileId);
}
