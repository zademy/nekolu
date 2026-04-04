/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import com.zademy.nekolu.dto.FolderInfo;
import com.zademy.nekolu.dto.NetworkStatsResponse;
import com.zademy.nekolu.dto.StorageStatsResponse;
import com.zademy.nekolu.dto.TelegramLimitsResponse;

/**
 * Defines the TDLib-backed Telegram operations used by the web and REST layers.
 * This contract owns chat and folder workflows, shared session access, download orchestration,
 * and runtime statistics retrieval.
 */
public interface TelegramService {

    /**
     * Gets the TDLib client for use by other services.
     *
     * @return the TDLib client
     */
    Client getClient();

    /**
     * Checks whether the session is authorized.
     *
     * @return true if authorized, false otherwise
     */
    boolean isAuthorized();

    /**
     * Sends a plain-text message to a chat.
     *
     * @param chatId the target chat ID
     * @param message the text to send
     * @return a future completed with the created Telegram message
     */
    CompletableFuture<TdApi.Message> sendTextMessage(long chatId, String message);

    /**
     * Replaces the text of an existing chat message.
     *
     * @param chatId the target chat ID
     * @param messageId the message to update
     * @param message the new text
     * @return a future completed with the updated Telegram message
     */
    CompletableFuture<TdApi.Message> editTextMessage(long chatId, long messageId, String message);

    /**
     * Searches messages inside a specific chat.
     *
     * @param chatId the chat to search
     * @param query the search query, or an empty string for recent messages
     * @param fromMessageId the message cursor used by TDLib pagination
     * @param limit the maximum number of messages to return
     * @return a future completed with the matching Telegram messages
     */
    CompletableFuture<List<TdApi.Message>> searchChatMessages(long chatId, String query, long fromMessageId, int limit);

    /**
     * Retrieves a specific Telegram message.
     *
     * @param chatId the chat that owns the message
     * @param messageId the Telegram message ID
     * @return a future completed with the resolved message
     */
    CompletableFuture<TdApi.Message> getMessage(long chatId, long messageId);

    /**
     * Starts a file download and returns a CompletableFuture.
     *
     * @param fileId the ID of the file to download
     * @return a CompletableFuture with the downloaded file
     */
    CompletableFuture<TdApi.File> downloadFile(int fileId);

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
    void trackUpload(int fileId, String tempFilePath);

    /**
     * Returns whether the file is still using a local staged source for upload.
     *
     * @param fileId the file ID
     * @return true when the upload source is still tracked locally
     */
    boolean isUploadTracked(int fileId);

    /**
     * Waits until the staged local upload source for a file is released.
     *
     * @param fileId the file ID
     * @return a future completed when the local upload source is no longer tracked
     */
    CompletableFuture<Void> waitForUploadRelease(int fileId);

    /**
     * Creates a "folder", internally represented as a private Telegram channel.
     *
     * @param title the folder title
     * @param description the optional description
     * @return a CompletableFuture with the created chat
     */
    CompletableFuture<TdApi.Chat> createFolder(String title, String description);

    /**
     * Lists all user folders (private Telegram channels).
     *
     * @return a CompletableFuture with the list of folders
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
