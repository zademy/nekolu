/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.model;

/**
 * Domain view of a file's local download state as exposed through the
 * TelegramService seam. Completion is decided by the Telegram module's update
 * handling: {@code downloaded} is true only when a complete local copy exists
 * on disk, making it the single semantic the workspace can trust.
 *
 * @param fileId the TDLib file identifier
 * @param size the total file size in bytes when known, 0 otherwise
 * @param downloadActive whether a background download is currently running
 * @param downloadedBytes the locally available prefix size in bytes
 * @param localPath the local path when any local copy exists, null otherwise
 * @param downloaded whether a complete local copy exists on disk
 */
public record TelegramFileState(
        long fileId,
        long size,
        boolean downloadActive,
        long downloadedBytes,
        String localPath,
        boolean downloaded) {

    /**
     * Progress percentage against the known size, 0 when the size is unknown.
     */
    public int progressPercent() {
        if (size <= 0 || downloadedBytes <= 0) {
            return downloaded ? 100 : 0;
        }
        return (int) Math.min(100, (downloadedBytes * 100) / size);
    }
}
