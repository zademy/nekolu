/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.zademy.nekolu.dto.FileInfoResponse;
import com.zademy.nekolu.model.LogicalFileMetadata;

/**
 * Defines the logical metadata operations applied on top of raw Telegram file data.
 */
public interface MetadataIndexService {
    CompletableFuture<FileInfoResponse> enrich(FileInfoResponse fileInfo);

    CompletableFuture<List<FileInfoResponse>> enrichAll(List<FileInfoResponse> files);

    CompletableFuture<Optional<LogicalFileMetadata>> findByFileId(long fileId);

    CompletableFuture<List<LogicalFileMetadata>> findByChecksum(String checksumSha256);

    CompletableFuture<Optional<LogicalFileMetadata>> findLatestVersion(long chatId, String virtualPath, String fileName);

    CompletableFuture<LogicalFileMetadata> registerUploadedFile(
        FileInfoResponse fileInfo,
        String checksumSha256,
        String virtualPath,
        List<String> tags,
        String origin,
        boolean archived
    );

    CompletableFuture<Optional<LogicalFileMetadata>> moveToTrash(long fileId);

    CompletableFuture<Optional<LogicalFileMetadata>> restore(long fileId);

    CompletableFuture<Optional<LogicalFileMetadata>> move(long fileId, String virtualPath);

    CompletableFuture<Optional<LogicalFileMetadata>> setArchived(long fileId, boolean archived);

    CompletableFuture<List<FileInfoResponse>> listTrash();
}
