/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.zademy.nekolu.dto.FolderInfo;
import com.zademy.nekolu.dto.NetworkStatsResponse;
import com.zademy.nekolu.dto.StorageStatsResponse;
import com.zademy.nekolu.dto.TelegramLimitsResponse;
import com.zademy.nekolu.model.TelegramFileMessage;
import com.zademy.nekolu.model.TelegramFileState;

/**
 * The seam between the workspace and Telegram. This contract owns the whole
 * TDLib protocol: readiness, rate limiting, timeouts, and error translation
 * live behind it, and every operation speaks in domain types — no TDLib
 * types cross the seam. Two adapters satisfy it: the TDLib-backed
 * implementation in production and an in-memory fake in tests.
 */
public interface TelegramService {

    /**
     * Checks whether the session is authorized.
     *
     * @return true if authorized, false otherwise
     */
    boolean isAuthorized();

    // ==================== FIRST-RUN AUTHENTICATION ====================

    /** The wizard waits for the user's phone number. */
    String AUTH_STATE_WAIT_PHONE_NUMBER = "WAIT_PHONE_NUMBER";
    /** The wizard waits for the verification code. */
    String AUTH_STATE_WAIT_CODE = "WAIT_CODE";
    /** The wizard waits for the two-step-verification password (2FA). */
    String AUTH_STATE_WAIT_PASSWORD = "WAIT_PASSWORD";
    /** The session is authenticated and the workspace is usable. */
    String AUTH_STATE_READY = "READY";

    /**
     * Current step of the first-run authentication wizard, driven by TDLib's
     * authorization updates.
     *
     * @return one of the AUTH_STATE_* constants
     */
    String getAuthState();

    /**
     * Submits the user's phone number to start authentication.
     *
     * @param phoneNumber the phone number in international format
     * @return a future completed when TDLib accepts the number
     */
    CompletableFuture<Void> submitPhoneNumber(String phoneNumber);

    /**
     * Submits the verification code sent to the user.
     *
     * @param code the verification code
     * @return a future completed when TDLib accepts the code
     */
    CompletableFuture<Void> submitAuthCode(String code);

    /**
     * Submits the two-step-verification password, required only when the
     * account has 2FA enabled.
     *
     * @param password the cloud password
     * @return a future completed when TDLib accepts the password
     */
    CompletableFuture<Void> submitAuthPassword(String password);

    // ==================== FILE MESSAGE OPERATIONS (DOMAIN TYPES) ====================

    /**
     * Lists the recent messages that carry files in a chat, newest first.
     * Messages without a file are skipped.
     *
     * @param chatId the chat (Saved Messages or a folder channel)
     * @param fromMessageId the TDLib pagination cursor, 0 to start from the newest
     * @param limit the maximum number of messages to retrieve
     * @return a future completed with the file messages found
     */
    CompletableFuture<List<TelegramFileMessage>> getFileMessages(long chatId, long fromMessageId, int limit);

    /**
     * Searches file messages across all chats, optionally constrained to a
     * workspace file type. This is the single source of the type-to-filter
     * mapping.
     *
     * @param query the text query, or empty for type-only browsing
     * @param type the workspace file type (photo, video, audio, document, voice,
     *             video_note), or null/blank/"all" for any type
     * @param offset the TDLib search offset cursor, or empty to start fresh
     * @param limit the maximum number of messages to retrieve
     * @return a future completed with the matching file messages
     */
    CompletableFuture<List<TelegramFileMessage>> searchFileMessages(String query, String type, String offset, int limit);

    /**
     * Resolves the file message carried by a specific message.
     *
     * @param chatId the chat that owns the message
     * @param messageId the Telegram message ID
     * @return a future completed with the file message, or failed when the
     *         message does not exist or carries no file
     */
    CompletableFuture<TelegramFileMessage> getFileMessage(long chatId, long messageId);

    /**
     * Single download contract: starts a background download and returns the
     * initial state immediately, without waiting for the transfer. Completion
     * is observed later through {@link #getFileState(long)}; this is the only
     * way to start a download through the seam.
     *
     * @param fileId the TDLib file identifier
     * @return a future completed with the file state right after the download
     *         request was accepted
     */
    CompletableFuture<TelegramFileState> startDownload(long fileId);

    /**
     * Consults the current local state of a file: size, progress, local path,
     * and whether a complete local copy exists on disk.
     *
     * @param fileId the TDLib file identifier
     * @return a future completed with the current file state, or failed when
     *         the file is unknown to Telegram
     */
    CompletableFuture<TelegramFileState> getFileState(long fileId);

    /**
     * Uploads a local file to a chat as a document and returns the created
     * file message. The staged local source is registered for upload
     * tracking: it is cleaned up by the Telegram module when the upload
     * completes.
     *
     * @param chatId the target chat (Saved Messages or a folder channel)
     * @param filePath the absolute path of the staged local source
     * @param caption the optional caption
     * @return a future completed with the created file message
     */
    CompletableFuture<TelegramFileMessage> sendDocument(long chatId, String filePath, String caption);

    /**
     * Uploads a local image to a chat as a photo and returns the created
     * file message, with the same staging contract as
     * {@link #sendDocument(long, String, String)}.
     *
     * @param chatId the target chat
     * @param filePath the absolute path of the staged local source
     * @param caption the optional caption
     * @return a future completed with the created file message
     */
    CompletableFuture<TelegramFileMessage> sendPhoto(long chatId, String filePath, String caption);

    /**
     * Deletes messages from a chat, locally or for everyone.
     *
     * @param chatId the chat that owns the messages
     * @param messageIds the Telegram message IDs to delete
     * @param revoke true to delete for everyone, false to delete only locally
     * @return a future completed when the deletion is acknowledged
     */
    CompletableFuture<Void> deleteMessages(long chatId, List<Long> messageIds, boolean revoke);

    /**
     * Drops the local TDLib copy of a file, freeing disk space. Best effort:
     * failures surface as a failed future callers may ignore.
     *
     * @param fileId the TDLib file identifier
     * @return a future completed when the local copy is dropped
     */
    CompletableFuture<Void> deleteLocalFile(long fileId);

    /**
     * Resolves the chat ID for the authenticated user's Saved Messages dialog.
     *
     * @return a future completed with the Saved Messages chat ID
     */
    CompletableFuture<Long> getOwnChatId();

    /**
     * Registers a local staging file for upload tracking.
     * The staged file is automatically deleted when the upload completes.
     *
     * @param fileId the file ID
     * @param tempFilePath the staged file path
     */
    void trackUpload(long fileId, String tempFilePath);

    /**
     * Returns whether the file is still using a local staged source for upload.
     *
     * @param fileId the file ID
     * @return true when the upload source is still tracked locally
     */
    boolean isUploadTracked(long fileId);

    /**
     * Waits until the staged local upload source for a file is released.
     *
     * @param fileId the file ID
     * @return a future completed when the local upload source is no longer tracked
     */
    CompletableFuture<Void> waitForUploadRelease(long fileId);

    // ==================== FOLDERS ====================

    /**
     * Creates a "folder", internally represented as a private Telegram channel.
     *
     * @param title the folder title
     * @param description the optional description
     * @return a future completed with the created folder
     */
    CompletableFuture<FolderInfo> createFolder(String title, String description);

    /**
     * Lists all user folders (private Telegram channels).
     *
     * @return a future completed with the list of folders
     */
    CompletableFuture<List<FolderInfo>> listFolders();

    /**
     * Deletes a folder (private Telegram channel).
     * It first leaves the chat and then removes it locally.
     *
     * @param chatId the ID of the chat to delete
     * @return a CompletableFuture that completes when the operation finishes
     */
    CompletableFuture<Void> deleteFolder(long chatId);

    // ==================== STATISTICS ====================

    /**
     * Gets quick TDLib local storage statistics.
     *
     * @return a CompletableFuture with storage statistics
     */
    CompletableFuture<StorageStatsResponse> getStorageStatisticsFast();

    /**
     * Gets TDLib network usage statistics.
     *
     * @param onlyCurrent if true, returns only data from the current TDLib launch
     * @return a CompletableFuture with network statistics
     */
    CompletableFuture<NetworkStatsResponse> getNetworkStatistics(boolean onlyCurrent);

    /**
     * Gets the configured limits for the Telegram account.
     *
     * @return a CompletableFuture with the account limits
     */
    CompletableFuture<TelegramLimitsResponse> getTelegramLimits();
}
