/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.model;

import java.util.List;

/**
 * Logical metadata associated with a Telegram file inside the virtual drive abstraction.
 */
public record LogicalFileMetadata(
    long metadataMessageId,
    String logicalFileId,
    long fileId,
    long fileMessageId,
    long fileChatId,
    String fileName,
    String virtualPath,
    List<String> tags,
    String checksumSha256,
    int version,
    long createdAt,
    long logicalModifiedAt,
    String origin,
    boolean archived,
    boolean trashed
) {}
