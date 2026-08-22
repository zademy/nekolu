/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import com.zademy.nekolu.constants.FileTypeConstants;
import com.zademy.nekolu.dto.FolderInfo;
import com.zademy.nekolu.dto.NetworkStatsResponse;
import com.zademy.nekolu.dto.StorageStatsResponse;
import com.zademy.nekolu.dto.TelegramLimitsResponse;
import com.zademy.nekolu.model.TelegramFileMessage;
import com.zademy.nekolu.model.TelegramFileState;

/**
 * In-memory adapter for the TelegramService seam, used by unit tests of the
 * file-management module. It documents the seam contract: ready/unauthorized/
 * uninitialized sessions fail with the standard illegal-state messages, and
 * the file-message operations answer from programmable state. Legacy
 * TDLib-typed operations are intentionally unsupported.
 */
public class FakeTelegramService implements TelegramService {

    public enum SessionState {
        READY, UNINITIALIZED, UNAUTHORIZED
    }

    private SessionState state = SessionState.READY;
    private final List<TelegramFileMessage> messages = new ArrayList<>();
    private final Map<Long, TelegramFileState> fileStates = new HashMap<>();
    private RuntimeException failure;
    private long ownChatId = 1L;

    public FakeTelegramService inState(SessionState state) {
        this.state = state;
        return this;
    }

    public FakeTelegramService withMessages(TelegramFileMessage... fileMessages) {
        this.messages.addAll(List.of(fileMessages));
        return this;
    }

    public FakeTelegramService withFileState(TelegramFileState fileState) {
        this.fileStates.put(fileState.fileId(), fileState);
        return this;
    }

    public FakeTelegramService failingWith(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    public FakeTelegramService withOwnChatId(long ownChatId) {
        this.ownChatId = ownChatId;
        return this;
    }

    private <T> CompletableFuture<T> guarded(Callable<T> answer) {
        if (state == SessionState.UNINITIALIZED) {
            return CompletableFuture.failedFuture(new IllegalStateException("Telegram client not initialized"));
        }
        if (state == SessionState.UNAUTHORIZED) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                "Unauthorized. Telegram requires authentication. Use the TDLib CLI client to authenticate first."));
        }
        if (failure != null) {
            return CompletableFuture.failedFuture(failure);
        }
        try {
            return CompletableFuture.completedFuture(answer.call());
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private interface Callable<T> {
        T call() throws Exception;
    }

    // ==================== FILE MESSAGE OPERATIONS ====================

    @Override
    public CompletableFuture<List<TelegramFileMessage>> getFileMessages(long chatId, long fromMessageId, int limit) {
        return guarded(() -> messages.stream()
            .filter(message -> message.chatId() == chatId)
            .filter(message -> fromMessageId == 0 || message.messageId() < fromMessageId)
            .limit(Math.max(limit, 0))
            .toList());
    }

    @Override
    public CompletableFuture<List<TelegramFileMessage>> searchFileMessages(String query, String type, String offset, int limit) {
        return guarded(() -> messages.stream()
            .filter(message -> matchesType(message, type))
            .filter(message -> query == null || query.isBlank()
                || (message.fileName() != null && message.fileName().toLowerCase().contains(query.toLowerCase())))
            .limit(Math.max(limit, 0))
            .toList());
    }

    @Override
    public CompletableFuture<TelegramFileMessage> getFileMessage(long chatId, long messageId) {
        return guarded(() -> messages.stream()
            .filter(message -> message.chatId() == chatId && message.messageId() == messageId)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Message not found")));
    }

    private boolean matchesType(TelegramFileMessage message, String type) {
        if (type == null || type.isBlank() || FileTypeConstants.ALL.equalsIgnoreCase(type)) {
            return true;
        }
        return type.equalsIgnoreCase(message.type());
    }

    // ==================== DOWNLOAD CONTRACT ====================

    @Override
    public CompletableFuture<TelegramFileState> startDownload(long fileId) {
        return guarded(() -> fileStates.getOrDefault(fileId,
            new TelegramFileState(fileId, 0, true, 0, null, false)));
    }

    @Override
    public CompletableFuture<TelegramFileState> getFileState(long fileId) {
        return guarded(() -> {
            TelegramFileState state = fileStates.get(fileId);
            if (state == null) {
                throw new RuntimeException("File not found: " + fileId);
            }
            return state;
        });
    }

    // ==================== SESSION / UPLOAD TRACKING ====================

    @Override
    public Client getClient() {
        return null;
    }

    @Override
    public boolean isAuthorized() {
        return state == SessionState.READY;
    }

    @Override
    public CompletableFuture<Long> getOwnChatId() {
        return guarded(() -> ownChatId);
    }

    @Override
    public void trackUpload(int fileId, String tempFilePath) {
        // no-op: upload tracking is real-adapter behavior
    }

    @Override
    public boolean isUploadTracked(int fileId) {
        return false;
    }

    @Override
    public CompletableFuture<Void> waitForUploadRelease(int fileId) {
        return CompletableFuture.completedFuture(null);
    }

    // ==================== LEGACY OPERATIONS (UNSUPPORTED) ====================

    @Override
    public CompletableFuture<TdApi.Message> sendTextMessage(long chatId, String message) {
        return unsupported();
    }

    @Override
    public CompletableFuture<TdApi.Message> editTextMessage(long chatId, long messageId, String message) {
        return unsupported();
    }

    @Override
    public CompletableFuture<List<TdApi.Message>> searchChatMessages(long chatId, String query, long fromMessageId, int limit) {
        return unsupported();
    }

    @Override
    public CompletableFuture<TdApi.Message> getMessage(long chatId, long messageId) {
        return unsupported();
    }

    @Override
    public CompletableFuture<TdApi.File> downloadFile(int fileId) {
        return unsupported();
    }

    @Override
    public CompletableFuture<TdApi.Chat> createFolder(String title, String description) {
        return unsupported();
    }

    @Override
    public CompletableFuture<List<FolderInfo>> listFolders() {
        return unsupported();
    }

    @Override
    public CompletableFuture<Void> deleteFolder(long chatId) {
        return unsupported();
    }

    @Override
    public CompletableFuture<StorageStatsResponse> getStorageStatisticsFast() {
        return unsupported();
    }

    @Override
    public CompletableFuture<NetworkStatsResponse> getNetworkStatistics(boolean onlyCurrent) {
        return unsupported();
    }

    @Override
    public CompletableFuture<TelegramLimitsResponse> getTelegramLimits() {
        return unsupported();
    }

    private static <T> CompletableFuture<T> unsupported() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
            "Legacy TDLib-typed operation: not part of the tested seam contract"));
    }
}
