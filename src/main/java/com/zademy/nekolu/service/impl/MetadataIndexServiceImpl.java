/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import com.zademy.nekolu.dto.FileInfoResponse;
import com.zademy.nekolu.model.LogicalFileMetadata;
import com.zademy.nekolu.service.MetadataIndexService;

/**
 * Default metadata-index implementation that enriches file responses with virtual-drive defaults.
 */
@Service
public class MetadataIndexServiceImpl implements MetadataIndexService {
    private static final String DEFAULT_PATH = "/";
    private static final String DEFAULT_ORIGIN = "telegram-upload";

    @Override
    public CompletableFuture<FileInfoResponse> enrich(FileInfoResponse fileInfo) {
        return CompletableFuture.completedFuture(applyDefaults(fileInfo));
    }

    @Override
    public CompletableFuture<List<FileInfoResponse>> enrichAll(List<FileInfoResponse> files) {
        return CompletableFuture.completedFuture(files.stream().map(this::applyDefaults).toList());
    }

    @Override
    public CompletableFuture<Optional<LogicalFileMetadata>> findByFileId(long fileId) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<LogicalFileMetadata>> findByChecksum(String checksumSha256) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<Optional<LogicalFileMetadata>> findLatestVersion(long chatId, String virtualPath, String fileName) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<LogicalFileMetadata> registerUploadedFile(
        FileInfoResponse fileInfo,
        String checksumSha256,
        String virtualPath,
        List<String> tags,
        String origin,
        boolean archived
    ) {
        long now = System.currentTimeMillis();
        return CompletableFuture.completedFuture(new LogicalFileMetadata(
            0,
            UUID.randomUUID().toString(),
            fileInfo.fileId(),
            fileInfo.messageId(),
            fileInfo.chatId(),
            fileInfo.fileName(),
            normalizePath(virtualPath),
            normalizeTags(tags),
            checksumSha256,
            1,
            now,
            now,
            normalizeOrigin(origin),
            archived,
            false
        ));
    }

    @Override
    public CompletableFuture<Optional<LogicalFileMetadata>> moveToTrash(long fileId) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<LogicalFileMetadata>> restore(long fileId) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<LogicalFileMetadata>> move(long fileId, String virtualPath) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<LogicalFileMetadata>> setArchived(long fileId, boolean archived) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<FileInfoResponse>> listTrash() {
        return CompletableFuture.completedFuture(List.of());
    }

    private FileInfoResponse applyDefaults(FileInfoResponse fileInfo) {
        String downloadUrl = fileInfo.isDownloaded() ? "/api/telegram/files/" + fileInfo.fileId() + "/content" : null;
        String thumbnailUrl = fileInfo.thumbnailPath() != null && !fileInfo.thumbnailPath().isBlank()
            ? "/api/telegram/files/" + fileInfo.fileId() + "/thumbnail"
            : null;

        return new FileInfoResponse(
            fileInfo.messageId(),
            fileInfo.chatId(),
            fileInfo.fileId(),
            fileInfo.fileName(),
            fileInfo.fileSize(),
            fileInfo.mimeType(),
            fileInfo.type(),
            fileInfo.width(),
            fileInfo.height(),
            fileInfo.duration(),
            fileInfo.thumbnailPath(),
            fileInfo.date(),
            fileInfo.isDownloaded(),
            fileInfo.localPath(),
            fileInfo.isDownloaded(),
            downloadUrl,
            "/api/telegram/files/" + fileInfo.fileId() + "/stream",
            thumbnailUrl,
            "telegram-" + fileInfo.fileId(),
            DEFAULT_PATH,
            List.of(),
            null,
            1,
            fileInfo.date() * 1000L,
            DEFAULT_ORIGIN,
            false,
            false
        );
    }

    private String normalizePath(String virtualPath) {
        if (virtualPath == null || virtualPath.isBlank()) {
            return DEFAULT_PATH;
        }
        String normalized = virtualPath.trim().replace('\\', '/');
        if (!normalized.startsWith(DEFAULT_PATH)) {
            normalized = DEFAULT_PATH + normalized;
        }
        normalized = normalized.replaceAll("/+", "/");
        if (normalized.length() > 1 && normalized.endsWith(DEFAULT_PATH)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? DEFAULT_PATH : normalized;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
            .filter(tag -> tag != null && !tag.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
    }

    private String normalizeOrigin(String origin) {
        return origin == null || origin.isBlank() ? DEFAULT_ORIGIN : origin.trim();
    }
}
