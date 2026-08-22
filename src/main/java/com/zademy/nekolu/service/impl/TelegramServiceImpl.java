/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.zademy.nekolu.constants.FileTypeConstants;
import com.zademy.nekolu.constants.ServiceDefaults;
import com.zademy.nekolu.config.TelegramConfig;
import com.zademy.nekolu.dto.FolderInfo;
import com.zademy.nekolu.dto.NetworkStatsResponse;
import com.zademy.nekolu.dto.StorageStatsResponse;
import com.zademy.nekolu.dto.TelegramLimitsResponse;
import com.zademy.nekolu.service.TelegramService;

import jakarta.annotation.PostConstruct;

/**
 * TDLib-backed implementation of Telegram operations such as authorization, downloads, folders, and telemetry retrieval.
 */
@Service
public class TelegramServiceImpl implements TelegramService {
    private static final Logger logger = LoggerFactory.getLogger(TelegramServiceImpl.class);
    private static final String INTERNAL_INDEX_TITLE = "TGDrive Internal Index";

    private final TelegramConfig telegramConfig;
    private final TelegramRateLimiter rateLimiter;
    private Client client;
    private volatile boolean isAuthorized = false;
    private final ConcurrentHashMap<Integer, CompletableFuture<TdApi.File>> pendingDownloads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> pendingUploads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CompletableFuture<Void>> pendingUploadReleases = new ConcurrentHashMap<>();
    private volatile CompletableFuture<Long> ownChatIdFuture;
    private volatile boolean internalIndexCleanupAttempted = false;

    public TelegramServiceImpl(TelegramConfig telegramConfig, TelegramRateLimiter rateLimiter) {
        this.telegramConfig = telegramConfig;
        this.rateLimiter = rateLimiter;
    }

    @PostConstruct
    public void init() {
        client = Client.create(new UpdateHandler(), null, null);
        setTdlibParameters();
    }

    // ==================== TDLIB REQUEST GUARD ====================

    /**
     * Single point through which every public operation sends its TDLib request:
     * readiness preconditions, rate limiting, error translation, and a request
     * timeout. The permit is held until the returned future completes.
     */
    private <T extends TdApi.Object> CompletableFuture<T> send(TdApi.Function<T> request) {
        CompletableFuture<T> failed = TdLibPreconditions.requireReady(client, isAuthorized);
        if (failed != null) return failed;

        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            holdPermitUntil(future);
        } catch (RuntimeException e) {
            // The limiter throws synchronously; surface it through the future
            // so asynchronous callers see a failed operation instead of an
            // exception escaping the service call.
            return CompletableFuture.failedFuture(e);
        }
        dispatch(request, future);
        return future.orTimeout(ServiceDefaults.TDLIB_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Acquires a rate-limiter permit that is released exactly once when the
     * given future completes, successfully or not.
     */
    private void holdPermitUntil(CompletableFuture<?> future) {
        rateLimiter.acquire();
        future.whenComplete((result, error) -> rateLimiter.release());
    }

    /**
     * Sends a TDLib request without readiness preconditions or rate limiting,
     * but still bounded by the request timeout. Reserved for sub-requests of
     * an operation already holding a permit (folder listing fan-out) and
     * best-effort cleanup, where blocking on the limiter could stall TDLib
     * callbacks.
     */
    private <T extends TdApi.Object> CompletableFuture<T> sendUnguarded(TdApi.Function<T> request) {
        CompletableFuture<T> future = new CompletableFuture<>();
        dispatch(request, future);
        return future.orTimeout(ServiceDefaults.TDLIB_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Core error translation shared by every outgoing request: a TDLib error
     * becomes a failed future carrying the Telegram message; any other
     * response completes the future with its typed value.
     */
    @SuppressWarnings("unchecked")
    private <T extends TdApi.Object> void dispatch(TdApi.Function<T> request, CompletableFuture<T> target) {
        try {
            client.send(request, result -> {
                if (result instanceof TdApi.Error error) {
                    target.completeExceptionally(new RuntimeException(
                        "Telegram error %d: %s".formatted(error.code, error.message)));
                } else {
                    try {
                        target.complete((T) result);
                    } catch (ClassCastException e) {
                        target.completeExceptionally(new RuntimeException("Unexpected TDLib response"));
                    }
                }
            });
        } catch (RuntimeException e) {
            target.completeExceptionally(e);
        }
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    /**
     * Gets the TDLib client for use by other services.
     */
    @Override
    public Client getClient() {
        return client;
    }

    /**
     * Checks whether the session is authorized.
     */
    @Override
    public boolean isAuthorized() {
        return isAuthorized;
    }

    /**
     * Bootstrap request sent before authorization exists, deliberately outside
     * the request guard: readiness preconditions do not apply yet and a
     * failure here is only logged, not surfaced to callers.
     */
    private void setTdlibParameters() {
        TdApi.SetTdlibParameters params = new TdApi.SetTdlibParameters();
        params.useTestDc = telegramConfig.isUseTestDc();
        params.apiId = telegramConfig.getApiId();
        params.apiHash = telegramConfig.getApiHash();
        params.systemLanguageCode = telegramConfig.getSystemLanguageCode();
        params.deviceModel = telegramConfig.getDeviceModel();
        params.systemVersion = telegramConfig.getSystemVersion();
        params.applicationVersion = telegramConfig.getApplicationVersion();
        params.useSecretChats = telegramConfig.isUseSecretChats();
        params.useMessageDatabase = telegramConfig.isUseMessageDatabase();
        params.useChatInfoDatabase = telegramConfig.isUseChatInfoDatabase();
        params.useFileDatabase = telegramConfig.isUseFileDatabase();
        params.databaseDirectory = telegramConfig.getDatabaseDirectory();
        params.filesDirectory = telegramConfig.getFilesDirectory();

        client.send(params, object -> {
            if (object instanceof TdApi.Ok) {
                logger.info("TDLib parameters configured");
            } else if (object instanceof TdApi.Error error) {
                logger.error("Error setting TDLib parameters: {}", error.message);
            }
        });
    }

    @Override
    public CompletableFuture<TdApi.Message> sendTextMessage(long chatId, String message) {
        TdApi.FormattedText formattedText = new TdApi.FormattedText(message, null);
        TdApi.InputMessageText messageText = new TdApi.InputMessageText(formattedText, null, false);
        TdApi.SendMessage sendMessage = new TdApi.SendMessage();
        sendMessage.chatId = chatId;
        sendMessage.inputMessageContent = messageText;

        return send(sendMessage);
    }

    @Override
    public CompletableFuture<TdApi.Message> editTextMessage(long chatId, long messageId, String message) {
        TdApi.EditMessageText editMessageText = new TdApi.EditMessageText();
        editMessageText.chatId = chatId;
        editMessageText.messageId = messageId;
        editMessageText.replyMarkup = null;
        editMessageText.inputMessageContent = new TdApi.InputMessageText(new TdApi.FormattedText(message, null), null, false);

        return send(editMessageText);
    }

    @Override
    public CompletableFuture<List<TdApi.Message>> searchChatMessages(long chatId, String query, long fromMessageId, int limit) {
        TdApi.SearchChatMessages search = new TdApi.SearchChatMessages();
        search.chatId = chatId;
        search.query = query != null ? query : "";
        search.senderId = null;
        search.fromMessageId = fromMessageId;
        search.offset = 0;
        search.limit = Math.max(1, Math.min(limit, 100));
        search.filter = null;

        return send(search).thenApply(foundMessages -> List.of(foundMessages.messages));
    }

    @Override
    public CompletableFuture<TdApi.Message> getMessage(long chatId, long messageId) {
        TdApi.GetMessages getMessages = new TdApi.GetMessages();
        getMessages.chatId = chatId;
        getMessages.messageIds = new long[] { messageId };

        return send(getMessages).thenCompose(messages -> {
            if (messages.messages.length > 0 && messages.messages[0] != null) {
                return CompletableFuture.completedFuture(messages.messages[0]);
            }
            return CompletableFuture.failedFuture(new RuntimeException("Message not found"));
        });
    }

    /**
     * Starts a file download and returns a CompletableFuture.
     */
    @Override
    public CompletableFuture<TdApi.File> downloadFile(int fileId) {
        CompletableFuture<TdApi.File> failed = TdLibPreconditions.requireReady(client, isAuthorized);
        if (failed != null) return failed;

        // The download cycle holds its permit until UpdateFile completes it,
        // exactly one permit per download as before the guard existed.
        CompletableFuture<TdApi.File> downloadFuture = new CompletableFuture<>();
        try {
            holdPermitUntil(downloadFuture);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        pendingDownloads.put(fileId, downloadFuture);

        TdApi.DownloadFile download = new TdApi.DownloadFile();
        download.fileId = fileId;
        download.priority = 1;
        download.offset = 0;
        download.limit = 0;
        download.synchronous = false;

        // Only the initial request is bounded by the request timeout; the
        // cycle itself completes via UpdateFile no matter how long the
        // transfer takes.
        sendUnguarded(download)
            .orTimeout(ServiceDefaults.TDLIB_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .whenComplete((initial, error) -> {
                if (error != null) {
                    pendingDownloads.remove(fileId);
                    downloadFuture.completeExceptionally(unwrap(error));
                }
            });

        return downloadFuture;
    }

    @Override
    public CompletableFuture<Long> getOwnChatId() {
        CompletableFuture<Long> cachedFuture = ownChatIdFuture;
        if (cachedFuture != null) {
            return cachedFuture;
        }

        synchronized (this) {
            if (ownChatIdFuture != null) {
                return ownChatIdFuture;
            }
            ownChatIdFuture = new CompletableFuture<>();
        }

        CompletableFuture<Long> future = ownChatIdFuture;

        if (telegramConfig.getUserId() == 0) {
            future.completeExceptionally(new IllegalStateException("telegram.user.id is not configured in properties"));
            synchronized (this) {
                ownChatIdFuture = null;
            }
            return future;
        }

        TdApi.CreatePrivateChat createChat = new TdApi.CreatePrivateChat();
        createChat.userId = telegramConfig.getUserId();
        createChat.force = false;

        send(createChat).whenComplete((chat, error) -> {
            if (error != null) {
                future.completeExceptionally(unwrap(error));
                synchronized (this) {
                    ownChatIdFuture = null;
                }
            } else {
                future.complete(chat.id);
            }
        });

        return future;
    }

    private class UpdateHandler implements Client.ResultHandler {
        @Override
        public void onResult(TdApi.Object object) {
            if (object instanceof TdApi.UpdateAuthorizationState authState) {
                handleAuthorizationState(authState.authorizationState);
            } else if (object instanceof TdApi.UpdateFile updateFile) {
                handleUpdateFile(updateFile);
            }
        }
    }

    @Override
    public void trackUpload(int fileId, String tempFilePath) {
        pendingUploads.put(fileId, tempFilePath);
    }

    @Override
    public boolean isUploadTracked(int fileId) {
        return pendingUploads.containsKey(fileId);
    }

    @Override
    public CompletableFuture<Void> waitForUploadRelease(int fileId) {
        if (!pendingUploads.containsKey(fileId)) {
            return CompletableFuture.completedFuture(null);
        }
        return pendingUploadReleases.computeIfAbsent(fileId, _ignored -> new CompletableFuture<>());
    }

    private void handleUpdateFile(TdApi.UpdateFile update) {
        TdApi.File file = update.file;

        // Handle uploads in progress
        String tempPath = pendingUploads.get(file.id);
        if (tempPath != null && file.remote != null) {
            if (!file.remote.isUploadingActive && file.remote.isUploadingCompleted) {
                // Upload completed, delete local staged source and clear TDLib local state
                java.io.File tempFile = new java.io.File(tempPath);
                if (tempFile.exists() && tempFile.delete()) {
                    logger.info("[Upload] Local staged file deleted after upload completed: {}", tempPath);
                }
                pendingUploads.remove(file.id);
                CompletableFuture<Void> releaseFuture = pendingUploadReleases.remove(file.id);
                if (releaseFuture != null && !releaseFuture.isDone()) {
                    releaseFuture.complete(null);
                }
                try {
                    TdApi.DeleteFile deleteFile = new TdApi.DeleteFile();
                    deleteFile.fileId = file.id;
                    sendUnguarded(deleteFile).exceptionally(result -> {
                        logger.debug("[Upload] Could not clear local TDLib file state for {}: {}", file.id, result.getMessage());
                        return null;
                    });
                } catch (Exception e) {
                    logger.debug("[Upload] Could not request local TDLib cleanup for {}: {}", file.id, e.getMessage());
                }
                return;
            } else if (file.remote.isUploadingActive) {
                // Upload in progress - optional log
                int progress = file.remote.uploadedSize > 0 && file.expectedSize > 0
                    ? (int) ((file.remote.uploadedSize * 100) / file.expectedSize)
                    : 0;
                if (progress % 20 == 0) { // Log every 20%
                    logger.info("[Upload] File {} progress: {}%", file.id, progress);
                }
                return;
            }
        }

        // Handle downloads (existing code)
        CompletableFuture<TdApi.File> future = pendingDownloads.get(file.id);

        if (future != null) {
            if (file.local != null && file.local.isDownloadingCompleted) {
                future.complete(file);
                pendingDownloads.remove(file.id);
                logger.info("File downloaded: {} -> {}", file.id, file.local.path);
            } else if (file.local != null && !file.local.isDownloadingActive && file.local.downloadedPrefixSize == 0) {
                // Download canceled or failed
                future.completeExceptionally(new RuntimeException("Download was cancelled or failed"));
                pendingDownloads.remove(file.id);
            } else {
                // Download in progress - optional log
                if (file.local != null && file.expectedSize > 0) {
                    int progress = (int) ((file.local.downloadedPrefixSize * 100) / file.expectedSize);
                    logger.info("Downloading file {}: {}%", file.id, progress);
                }
            }
        }
    }

    private void handleAuthorizationState(TdApi.AuthorizationState state) {
        if (state instanceof TdApi.AuthorizationStateReady) {
            isAuthorized = true;
            logger.info("TDLib ready and authorized");
            // Run off the TDLib callback thread: the cleanup performs
            // rate-limited requests that must never stall update delivery.
            CompletableFuture.runAsync(this::cleanupLegacyInternalIndexChat);
        } else if (state instanceof TdApi.AuthorizationStateWaitTdlibParameters) {
            logger.info("Waiting for TDLib parameters...");
        } else if (state instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            logger.warn("Telegram requires authentication:");
            logger.warn("1. Run: java -cp lib/tdlib.jar org.drinkless.tdlib.example.Example");
            logger.warn("2. Enter your phone number");
            logger.warn("3. Enter the verification code");
            logger.warn("4. Restart this application");
        } else if (state instanceof TdApi.AuthorizationStateWaitCode) {
            logger.info("Waiting for verification code...");
        } else if (state instanceof TdApi.AuthorizationStateClosed) {
            isAuthorized = false;
        }
    }

    /**
     * Creates a "folder", internally represented as a private Telegram channel.
     */
    @Override
    public CompletableFuture<TdApi.Chat> createFolder(String title, String description) {
        TdApi.CreateNewSupergroupChat request = new TdApi.CreateNewSupergroupChat();
        request.title = title;
        request.isChannel = true;
        request.isForum = false;
        request.description = description != null ? description : "";
        request.messageAutoDeleteTime = 0;
        request.forImport = false;

        return send(request).thenApply(chat -> {
            logger.info("Folder created: {} (ID: {})", chat.title, chat.id);
            return chat;
        });
    }

    /**
     * Lists all user folders (private Telegram channels).
     */
    @Override
    public CompletableFuture<List<FolderInfo>> listFolders() {
        TdApi.GetChats getChats = new TdApi.GetChats();
        getChats.chatList = new TdApi.ChatListMain();
        getChats.limit = ServiceDefaults.DEFAULT_CHAT_LIST_LIMIT;

        return send(getChats).thenCompose(chats -> {
            if (chats.chatIds.length == 0) {
                return CompletableFuture.completedFuture(new ArrayList<FolderInfo>());
            }

            List<FolderInfo> folders = new ArrayList<>();
            List<CompletableFuture<Void>> chatFutures = new ArrayList<>();

            for (long chatId : chats.chatIds) {
                CompletableFuture<Void> chatFuture = getChatInfo(chatId)
                    .thenAccept(folderInfo -> {
                        if (folderInfo != null) {
                            synchronized (folders) {
                                folders.add(folderInfo);
                            }
                        }
                    })
                    .exceptionally(ex -> {
                        logger.warn("Error getting chat info {}: {}", chatId, ex.getMessage());
                        return null;
                    });
                chatFutures.add(chatFuture);
            }

            return CompletableFuture.allOf(chatFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> folders);
        });
    }

    /**
     * Gets chat information if it is a channel (folder).
     * Sub-requests run unguarded: the enclosing listFolders operation already
     * holds a permit, and rate-limiting a fan-out from TDLib callback threads
     * could stall the client.
     */
    private CompletableFuture<FolderInfo> getChatInfo(long chatId) {
        return sendUnguarded(new TdApi.GetChat(chatId))
            .thenCompose(chat -> {
                // Only channels (not groups) are folders
                if (!(chat.type instanceof TdApi.ChatTypeSupergroup supergroupType) || !supergroupType.isChannel) {
                    return CompletableFuture.completedFuture(null);
                }

                return sendUnguarded(new TdApi.GetSupergroup(supergroupType.supergroupId))
                    .thenCompose(supergroup -> sendUnguarded(new TdApi.GetSupergroupFullInfo(supergroupType.supergroupId))
                        .thenApply(fullInfo -> new FolderInfo(
                            chat.id,
                            chat.title,
                            fullInfo.description != null ? fullInfo.description : "",
                            supergroup.memberCount,
                            supergroup.date
                        )));
            });
    }

    /**
     * Best-effort legacy cleanup, unguarded for the same reason as the folder
     * listing fan-out: it must never block TDLib callback threads.
     */
    private CompletableFuture<Long> findInternalIndexChatId() {
        TdApi.GetChats getChats = new TdApi.GetChats();
        getChats.chatList = new TdApi.ChatListMain();
        getChats.limit = ServiceDefaults.DEFAULT_CHAT_LIST_LIMIT;

        return sendUnguarded(getChats).thenCompose(chats -> {
            if (chats.chatIds.length == 0) {
                return CompletableFuture.completedFuture(0L);
            }

            List<CompletableFuture<Long>> futures = new ArrayList<>();
            for (long chatId : chats.chatIds) {
                futures.add(getChatIfInternalIndex(chatId).exceptionally(ex -> 0L));
            }

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                    .map(CompletableFuture::join)
                    .filter(foundChatId -> foundChatId != null && foundChatId != 0L)
                    .findFirst()
                    .orElse(0L));
        });
    }

    private CompletableFuture<Long> getChatIfInternalIndex(long chatId) {
        return sendUnguarded(new TdApi.GetChat(chatId)).thenApply(chat ->
            chat.type instanceof TdApi.ChatTypeSupergroup supergroupType
                && supergroupType.isChannel
                && isInternalIndexChat(chat.title)
            ? chat.id : 0L);
    }

    private boolean isInternalIndexChat(String title) {
        return INTERNAL_INDEX_TITLE.equalsIgnoreCase(title != null ? title.trim() : "");
    }

    private void cleanupLegacyInternalIndexChat() {
        if (internalIndexCleanupAttempted) {
            return;
        }
        internalIndexCleanupAttempted = true;

        findInternalIndexChatId()
            .thenCompose(chatId -> {
                if (chatId == null || chatId == 0L) {
                    return CompletableFuture.completedFuture(null);
                }
                logger.info("Removing legacy internal TGDrive index chat: {}", chatId);
                return deleteFolder(chatId);
            })
            .exceptionally(ex -> {
                logger.warn("Could not remove legacy internal index chat: {}", ex.getMessage());
                return null;
            });
    }

    /**
     * Deletes a folder (private Telegram channel).
     * It first leaves the chat and then deletes it locally.
     */
    @Override
    public CompletableFuture<Void> deleteFolder(long chatId) {
        return send(new TdApi.LeaveChat(chatId))
            .exceptionally(ex -> {
                logger.warn("Error leaving chat (it may no longer be a member): {}", ex.getMessage());
                // Continue trying to delete the chat locally
                return null;
            })
            .thenCompose(v -> send(new TdApi.DeleteChat(chatId)))
            .thenRun(() -> logger.info("Folder deleted: ID {}", chatId));
    }

    // ==================== STATISTICS METHODS ====================

    /**
     * Gets quick TDLib local storage statistics.
     */
    @Override
    public CompletableFuture<StorageStatsResponse> getStorageStatisticsFast() {
        return send(new TdApi.GetStorageStatisticsFast()).thenApply(stats -> new StorageStatsResponse(
            stats.filesSize,
            stats.fileCount,
            stats.databaseSize,
            stats.languagePackDatabaseSize,
            stats.logSize
        ));
    }

    /**
     * Gets TDLib network usage statistics.
     *
     * @param onlyCurrent if true, returns only data since the current TDLib launch
     */
    @Override
    public CompletableFuture<NetworkStatsResponse> getNetworkStatistics(boolean onlyCurrent) {
        return send(new TdApi.GetNetworkStatistics(onlyCurrent)).thenApply(stats -> {
            List<NetworkStatsResponse.NetworkFileEntry> fileEntries = new ArrayList<>();
            List<NetworkStatsResponse.NetworkCallEntry> callEntries = new ArrayList<>();

            for (TdApi.NetworkStatisticsEntry entry : stats.entries) {
                if (entry instanceof TdApi.NetworkStatisticsEntryFile fileEntry) {
                    fileEntries.add(new NetworkStatsResponse.NetworkFileEntry(
                        mapFileType(fileEntry.fileType),
                        mapNetworkType(fileEntry.networkType),
                        fileEntry.sentBytes,
                        fileEntry.receivedBytes
                    ));
                } else if (entry instanceof TdApi.NetworkStatisticsEntryCall callEntry) {
                    callEntries.add(new NetworkStatsResponse.NetworkCallEntry(
                        mapNetworkType(callEntry.networkType),
                        callEntry.sentBytes,
                        callEntry.receivedBytes
                    ));
                }
            }

            return new NetworkStatsResponse(stats.sinceDate, fileEntries, callEntries);
        });
    }

    /**
     * Gets the configured limits for the Telegram account.
     */
    @Override
    public CompletableFuture<TelegramLimitsResponse> getTelegramLimits() {
        CompletableFuture<Long> maxUpload = getOptionLong("upload_max_fileparts")
            .thenApply(parts -> parts > 0 ? parts * 524288L : 2147483648L) // 512KB per part, default 2GB
            .exceptionally(ex -> 2147483648L);
        CompletableFuture<Integer> maxBasicGroup = getOptionInt("basic_group_size_max").exceptionally(ex -> 200);
        CompletableFuture<Integer> maxSupergroup = getOptionInt("supergroup_size_max").exceptionally(ex -> 200000);
        CompletableFuture<Integer> maxFolders = getOptionInt("chat_folder_count_max").exceptionally(ex -> 10);

        return CompletableFuture.allOf(maxUpload, maxBasicGroup, maxSupergroup, maxFolders)
            .thenApply(v -> new TelegramLimitsResponse(
                maxUpload.join(),
                maxBasicGroup.join(),
                maxSupergroup.join(),
                maxFolders.join()
            ));
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private CompletableFuture<Long> getOptionLong(String name) {
        return send(new TdApi.GetOption(name)).thenApply(result -> {
            if (result instanceof TdApi.OptionValueInteger opt) {
                return opt.value;
            }
            return 0L;
        });
    }

    private CompletableFuture<Integer> getOptionInt(String name) {
        return getOptionLong(name).thenApply(Long::intValue);
    }

    private String mapFileType(TdApi.FileType fileType) {
        if (fileType instanceof TdApi.FileTypePhoto) {
            return FileTypeConstants.PHOTO;
        }
        if (fileType instanceof TdApi.FileTypeVideo) {
            return FileTypeConstants.VIDEO;
        }
        if (fileType instanceof TdApi.FileTypeAudio) {
            return FileTypeConstants.AUDIO;
        }
        if (fileType instanceof TdApi.FileTypeDocument) {
            return FileTypeConstants.DOCUMENT;
        }
        if (fileType instanceof TdApi.FileTypeVoiceNote) {
            return FileTypeConstants.VOICE;
        }
        if (fileType instanceof TdApi.FileTypeVideoNote) {
            return FileTypeConstants.VIDEO_NOTE;
        }
        if (fileType instanceof TdApi.FileTypeAnimation) {
            return FileTypeConstants.ANIMATION;
        }
        if (fileType instanceof TdApi.FileTypeSticker) {
            return FileTypeConstants.STICKER;
        }
        if (fileType instanceof TdApi.FileTypeThumbnail) {
            return FileTypeConstants.THUMBNAIL;
        }
        if (fileType instanceof TdApi.FileTypeProfilePhoto) {
            return FileTypeConstants.PROFILE_PHOTO;
        }
        if (fileType instanceof TdApi.FileTypeWallpaper) {
            return FileTypeConstants.WALLPAPER;
        }
        return FileTypeConstants.OTHER;
    }

    private String mapNetworkType(TdApi.NetworkType networkType) {
        if (networkType instanceof TdApi.NetworkTypeMobile) {
            return "mobile";
        }
        if (networkType instanceof TdApi.NetworkTypeMobileRoaming) {
            return "mobile_roaming";
        }
        if (networkType instanceof TdApi.NetworkTypeWiFi) {
            return "wifi";
        }
        if (networkType instanceof TdApi.NetworkTypeOther) {
            return "other";
        }
        return "unknown";
    }
}
