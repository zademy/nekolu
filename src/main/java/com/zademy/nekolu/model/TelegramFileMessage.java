/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.model;

/**
 * Domain view of a Telegram message that carries a file, as exposed through
 * the TelegramService seam. It carries the raw workspace facts (identifiers,
 * size, type, local download state); URL construction and logical enrichment
 * stay on the file-management side.
 *
 * @param messageId the Telegram message that carries the file
 * @param chatId the chat (Saved Messages or a folder channel) holding the message
 * @param fileId the TDLib file identifier
 * @param fileName the best available file name (original or derived)
 * @param fileSize the file size in bytes
 * @param mimeType the best available MIME type (original or derived)
 * @param type the workspace file type (photo, video, audio, document, voice, video_note)
 * @param width the width in pixels when it applies, null otherwise
 * @param height the height in pixels when it applies, null otherwise
 * @param duration the duration in seconds when it applies, null otherwise
 * @param thumbnailPath the local thumbnail path when already downloaded, null otherwise
 * @param date the message date as a Unix timestamp
 * @param downloaded whether a complete local copy exists on disk
 * @param localPath the local path when downloaded, null otherwise
 */
public record TelegramFileMessage(
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
        int date,
        boolean downloaded,
        String localPath) {
}
