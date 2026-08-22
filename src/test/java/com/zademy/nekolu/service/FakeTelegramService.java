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

import com.zademy.nekolu.constants.FileTypeConstants;
import com.zademy.nekolu.dto.FolderInfo;
import com.zademy.nekolu.dto.NetworkStatsResponse;
import com.zademy.nekolu.dto.StorageStatsResponse;
import com.zademy.nekolu.dto.TelegramLimitsResponse;
import com.zademy.nekolu.exception.TelegramNotInitializedException;
import com.zademy.nekolu.exception.TelegramNotFoundException;
import com.zademy.nekolu.exception.TelegramUnauthorizedException;
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
    private String authState = TelegramService.AUTH_STATE_READY;
    private final List<String> authSubmissions = new ArrayList<>();
    private final List<TelegramFileMessage> messages = new ArrayList<>();
    private final Map<Long, TelegramFileState> fileStates = new HashMap<>();
    private final Map<Integer, String> trackedUploads = new HashMap<>();
    private final List<String> deletedMessages = new ArrayList<>();
    private final List<Long> deletedLocalFiles = new ArrayList<>();
    private TelegramFileMessage uploadResult;
    private long nextMessageId = 1000;
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

    public FakeTelegramService withUploadResult(TelegramFileMessage uploadResult) {
        this.uploadResult = uploadResult;
        return this;
    }

    public List<String> deletedMessages() {
        return List.copyOf(deletedMessages);
    }

    public List<Long> deletedLocalFiles() {
        return List.copyOf(deletedLocalFiles);
    }

    public Map<Integer, String> trackedUploads() {
        return Map.copyOf(trackedUploads);
    }

    public FakeTelegramService failingWith(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    public FakeTelegramService withAuthState(String authState) {
        this.authState = authState;
        return this;
    }

    public List<String> authSubmissions() {
        return List.copyOf(authSubmissions);
    }

    public FakeTelegramService withOwnChatId(long ownChatId) {
        this.ownChatId = ownChatId;
        return this;
    }

    private <T> CompletableFuture<T> guarded(Callable<T> answer) {
        if (state == SessionState.UNINITIALIZED) {
            return CompletableFuture.failedFuture(new TelegramNotInitializedException());
        }
        if (state == SessionState.UNAUTHORIZED) {
            return CompletableFuture.failedFuture(new TelegramUnauthorizedException());
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
            .orElseThrow(() -> new TelegramNotFoundException("Message not found")));
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
                throw new TelegramNotFoundException("File not found: " + fileId);
            }
            return state;
        });
    }

    // ==================== UPLOAD / DELETE OPERATIONS ====================

    @Override
    public CompletableFuture<TelegramFileMessage> sendDocument(long chatId, String filePath, String caption) {
        return sendFile(chatId, filePath, "document");
    }

    @Override
    public CompletableFuture<TelegramFileMessage> sendPhoto(long chatId, String filePath, String caption) {
        return sendFile(chatId, filePath, "photo");
    }

    private CompletableFuture<TelegramFileMessage> sendFile(long chatId, String filePath, String type) {
        return guarded(() -> {
            TelegramFileMessage created = uploadResult != null
                ? uploadResult
                : new TelegramFileMessage(
                    nextMessageId++, chatId, nextMessageId * 10,
                    filePath.substring(filePath.lastIndexOf('/') + 1),
                    4096, null, type, null, null, null, null, 1700000000, false, null);
            trackedUploads.put((int) created.fileId(), filePath);
            return created;
        });
    }

    @Override
    public CompletableFuture<Void> deleteMessages(long chatId, List<Long> messageIds, boolean revoke) {
        return guarded(() -> {
            for (Long messageId : messageIds) {
                deletedMessages.add(chatId + ":" + messageId + ":" + revoke);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> deleteLocalFile(long fileId) {
        return guarded(() -> {
            deletedLocalFiles.add(fileId);
            return null;
        });
    }

    // ==================== SESSION / UPLOAD TRACKING ====================

    @Override
    public boolean isAuthorized() {
        return state == SessionState.READY;
    }

    // ==================== FIRST-RUN AUTHENTICATION ====================

    @Override
    public String getAuthState() {
        return authState;
    }

    @Override
    public CompletableFuture<Void> submitPhoneNumber(String phoneNumber) {
        return guarded(() -> {
            authSubmissions.add("phone:" + phoneNumber);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> submitAuthCode(String code) {
        return guarded(() -> {
            authSubmissions.add("code:" + code);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> submitAuthPassword(String password) {
        return guarded(() -> {
            authSubmissions.add("password:" + password);
            return null;
        });
    }

    @Override
    public CompletableFuture<Long> getOwnChatId() {
        return guarded(() -> ownChatId);
    }

    @Override
    public void trackUpload(long fileId, String tempFilePath) {
        trackedUploads.put((int) fileId, tempFilePath);
    }

    @Override
    public boolean isUploadTracked(long fileId) {
        return trackedUploads.containsKey((int) fileId);
    }

    @Override
    public CompletableFuture<Void> waitForUploadRelease(long fileId) {
        // The real adapter releases when the upload completes; the fake
        // releases immediately, simulating an already-finished transfer.
        trackedUploads.remove((int) fileId);
        return CompletableFuture.completedFuture(null);
    }

    // ==================== FOLDERS / STATISTICS (UNSUPPORTED) ====================

    @Override
    public CompletableFuture<FolderInfo> createFolder(String title, String description) {
        return guarded(() -> new FolderInfo(
            nextMessageId++,
            title,
            description != null ? description : "",
            1,
            (int) (System.currentTimeMillis() / 1000)));
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
