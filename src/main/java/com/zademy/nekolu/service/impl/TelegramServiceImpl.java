/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
    private Client client;
    private volatile boolean isAuthorized = false;
    private final ConcurrentHashMap<Integer, CompletableFuture<TdApi.File>> pendingDownloads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> pendingUploads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CompletableFuture<Void>> pendingUploadReleases = new ConcurrentHashMap<>();
    private volatile CompletableFuture<Long> ownChatIdFuture;
    private volatile boolean internalIndexCleanupAttempted = false;

    public TelegramServiceImpl(TelegramConfig telegramConfig) {
        this.telegramConfig = telegramConfig;
    }

    @PostConstruct
    public void init() {
        client = Client.create(new UpdateHandler(), null, null);
        setTdlibParameters();
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
        CompletableFuture<TdApi.Message> future = new CompletableFuture<>();

        if (client == null) {
            future.completeExceptionally(new IllegalStateException("Telegram client not initialized"));
            return future;
        }

        if (!isAuthorized) {
            future.completeExceptionally(new IllegalStateException("Unauthorized. Telegram requires authentication."));
            return future;
        }

        TdApi.FormattedText formattedText = new TdApi.FormattedText(message, null);
        TdApi.InputMessageText messageText = new TdApi.InputMessageText(formattedText, null, false);
        TdApi.SendMessage sendMessage = new TdApi.SendMessage();
        sendMessage.chatId = chatId;
        sendMessage.inputMessageContent = messageText;

        client.send(sendMessage, object -> {
            if (object instanceof TdApi.Message tdMessage) {
                future.complete(tdMessage);
            } else if (object instanceof TdApi.Error error) {
                future.completeExceptionally(new RuntimeException("Telegram error: " + error.message));
            } else {
                future.completeExceptionally(new RuntimeException("Unexpected TDLib response"));
            }
        });

        return future;
    }

    @Override
    public CompletableFuture<TdApi.Message> editTextMessage(long chatId, long messageId, String message) {
        CompletableFuture<TdApi.Message> future = new CompletableFuture<>();

        if (client == null) {
            future.completeExceptionally(new IllegalStateException("Telegram client not initialized"));
            return future;
        }

        if (!isAuthorized) {
            future.completeExceptionally(new IllegalStateException("Unauthorized. Telegram requires authentication."));
            return future;
        }

        TdApi.EditMessageText editMessageText = new TdApi.EditMessageText();
        editMessageText.chatId = chatId;
        editMessageText.messageId = messageId;
        editMessageText.replyMarkup = null;
        editMessageText.inputMessageContent = new TdApi.InputMessageText(new TdApi.FormattedText(message, null), null, false);

        client.send(editMessageText, object -> {
            if (object instanceof TdApi.Message tdMessage) {
                future.complete(tdMessage);
            } else if (object instanceof TdApi.Error error) {
                future.completeExceptionally(new RuntimeException("Telegram error: " + error.message));
            } else {
                future.completeExceptionally(new RuntimeException("Unexpected TDLib response"));
            }
        });

        return future;
    }

    @Override
    public CompletableFuture<List<TdApi.Message>> searchChatMessages(long chatId, String query, long fromMessageId, int limit) {
        CompletableFuture<List<TdApi.Message>> future = new CompletableFuture<>();

        if (client == null) {
            future.completeExceptionally(new IllegalStateException("Telegram client not initialized"));
            return future;
        }

        if (!isAuthorized) {
            future.completeExceptionally(new IllegalStateException("Unauthorized. Telegram requires authentication."));
            return future;
        }

        TdApi.SearchChatMessages search = new TdApi.SearchChatMessages();
        search.chatId = chatId;
        search.query = query != null ? query : "";
        search.senderId = null;
        search.fromMessageId = fromMessageId;
        search.offset = 0;
        search.limit = Math.max(1, Math.min(limit, 100));
        search.filter = null;

        client.send(search, result -> {
            if (result instanceof TdApi.FoundChatMessages foundMessages) {
                future.complete(List.of(foundMessages.messages));
            } else if (result instanceof TdApi.Error error) {
                future.completeExceptionally(new RuntimeException("Telegram error: " + error.message));
            } else {
                future.complete(List.of());
            }
        });

        return future;
    }

    @Override
    public CompletableFuture<TdApi.Message> getMessage(long chatId, long messageId) {
        CompletableFuture<TdApi.Message> future = new CompletableFuture<>();

        if (client == null) {
            future.completeExceptionally(new IllegalStateException("Telegram client not initialized"));
            return future;
        }

        TdApi.GetMessages getMessages = new TdApi.GetMessages();
        getMessages.chatId = chatId;
        getMessages.messageIds = new long[] { messageId };

        client.send(getMessages, result -> {
            if (result instanceof TdApi.Messages messages && messages.messages.length > 0 && messages.messages[0] != null) {
                future.complete(messages.messages[0]);
            } else if (result instanceof TdApi.Error error) {
                future.completeExceptionally(new RuntimeException("Telegram error: " + error.message));
            } else {
                future.completeExceptionally(new RuntimeException("Message not found"));
            }
        });

        return future;
    }

    /**
     * Starts a file download and returns a CompletableFuture.
     */
    @Override
    public CompletableFuture<TdApi.File> downloadFile(int fileId) {
        CompletableFuture<TdApi.File> future = new CompletableFuture<>();

        if (client == null) {
            future.completeExceptionally(new IllegalStateException("Telegram client not initialized"));
            return future;
        }

        pendingDownloads.put(fileId, future);

        TdApi.DownloadFile download = new TdApi.DownloadFile();
        download.fileId = fileId;
        download.priority = 1;
        download.offset = 0;
        download.limit = 0;
        download.synchronous = false;

        client.send(download, object -> {
            if (object instanceof TdApi.Error error) {
                pendingDownloads.remove(fileId);
                future.completeExceptionally(new RuntimeException("Error starting download: " + error.message));
            }
            // If it is OK, wait for UpdateFile to complete the future
        });

        return future;
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

        if (client == null) {
            future.completeExceptionally(new IllegalStateException("Telegram client not initialized"));
            synchronized (this) {
                ownChatIdFuture = null;
            }
            return future;
        }

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

        client.send(createChat, chatResult -> {
            if (chatResult instanceof TdApi.Chat chat) {
                future.complete(chat.id);
            } else if (chatResult instanceof TdApi.Error error) {
                future.completeExceptionally(new RuntimeException("Error creating chat: " + error.message));
                synchronized (this) {
                    ownChatIdFuture = null;
                }
            } else {
                future.completeExceptionally(new RuntimeException("Unexpected TDLib response"));
                synchronized (this) {
                    ownChatIdFuture = null;
                }
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
                    client.send(deleteFile, result -> {
                        if (result instanceof TdApi.Error error) {
                            logger.debug("[Upload] Could not clear local TDLib file state for {}: {}", file.id, error.message);
                        }
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
            cleanupLegacyInternalIndexChat();
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
        CompletableFuture<TdApi.Chat> future = new CompletableFuture<>();

        if (client == null) {
            future.completeExceptionally(new IllegalStateException("Telegram client not initialized"));
            return future;
        }

        if (!isAuthorized) {
            future.completeExceptionally(new IllegalStateException(
                "Unauthorized. Telegram requires authentication. " +
                "Use the TDLib CLI client to authenticate first."
            ));
            return future;
        }

        TdApi.CreateNewSupergroupChat request = new TdApi.CreateNewSupergroupChat();
        request.title = title;
        request.isChannel = true;
        request.isForum = false;
        request.description = description != null ? description : "";
        request.messageAutoDeleteTime = 0;
        request.forImport = false;

        client.send(request, result -> {
            if (result instanceof TdApi.Chat chat) {
                logger.info("Folder created: {} (ID: {})", chat.title, chat.id);
                future.complete(chat);
            } else if (result instanceof TdApi.Error error) {
                logger.error("Error creating folder: {}", error.message);
                future.completeExceptionally(new RuntimeException("Telegram error: " + error.message));
            }
        });

        return future;
    }

    /**
     * Lists all user folders (private Telegram channels).
     */
    @Override
    public CompletableFuture<List<FolderInfo>> listFolders() {
        CompletableFuture<List<FolderInfo>> future = new CompletableFuture<>();

        if (client == null) {
            future.completeExceptionally(new IllegalStateException("Telegram client not initialized"));
            return future;
        }

        if (!isAuthorized) {
            future.completeExceptionally(new IllegalStateException(
                "Unauthorized. Telegram requires authentication. " +
                "Use the TDLib CLI client to authenticate first."
            ));
            return future;
        }

        TdApi.GetChats getChats = new TdApi.GetChats();
        getChats.chatList = new TdApi.ChatListMain();
        getChats.limit = ServiceDefaults.DEFAULT_CHAT_LIST_LIMIT;

        client.send(getChats, result -> {
            if (result instanceof TdApi.Error error) {
                logger.error("Error getting chats: {}", error.message);
                future.completeExceptionally(new RuntimeException("Telegram error: " + error.message));
                return;
            }

            if (!(result instanceof TdApi.Chats chats)) {
                future.completeExceptionally(new RuntimeException("Unexpected response while fetching chats"));
                return;
            }

            if (chats.chatIds.length == 0) {
                future.complete(new ArrayList<>());
                return;
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

            CompletableFuture.allOf(chatFutures.toArray(new CompletableFuture[0]))
                .thenAccept(v -> future.complete(folders))
                .exceptionally(ex -> {
                    future.completeExceptionally(ex);
                    return null;
                });
        });

        return future;
    }

    /**
     * Gets chat information if it is a channel (folder).
     */
    private CompletableFuture<FolderInfo> getChatInfo(long chatId) {
        CompletableFuture<FolderInfo> future = new CompletableFuture<>();

        client.send(new TdApi.GetChat(chatId), result -> {
            if (result instanceof TdApi.Error error) {
                future.completeExceptionally(new RuntimeException("Error fetching chat: " + error.message));
                return;
            }

            if (!(result instanceof TdApi.Chat chat)) {
                future.complete(null);
                return;
            }

            // Check whether it is a supergroup (channel)
            if (!(chat.type instanceof TdApi.ChatTypeSupergroup supergroupType)) {
                future.complete(null);
                return;
            }

            // Only channels (not groups)
            if (!supergroupType.isChannel) {
                future.complete(null);
                return;
            }

            // Get supergroup details
            client.send(new TdApi.GetSupergroup(supergroupType.supergroupId), superResult -> {
                if (superResult instanceof TdApi.Error error) {
                    future.completeExceptionally(new RuntimeException("Error fetching supergroup: " + error.message));
                    return;
                }

                if (!(superResult instanceof TdApi.Supergroup supergroup)) {
                    future.complete(null);
                    return;
                }

                // Get full information for the description
                client.send(new TdApi.GetSupergroupFullInfo(supergroupType.supergroupId), fullInfoResult -> {
                    String description = "";
                    if (fullInfoResult instanceof TdApi.SupergroupFullInfo fullInfo) {
                        description = fullInfo.description != null ? fullInfo.description : "";
                    }

                    FolderInfo folderInfo = new FolderInfo(
                        chat.id,
                        chat.title,
                        description,
                        supergroup.memberCount,
                        supergroup.date
                    );

                    future.complete(folderInfo);
                });
            });
        });

        return future;
    }

    private CompletableFuture<Long> findInternalIndexChatId() {
        CompletableFuture<Long> future = new CompletableFuture<>();

        TdApi.GetChats getChats = new TdApi.GetChats();
        getChats.chatList = new TdApi.ChatListMain();
        getChats.limit = ServiceDefaults.DEFAULT_CHAT_LIST_LIMIT;

        client.send(getChats, result -> {
            if (result instanceof TdApi.Error error) {
                future.completeExceptionally(new RuntimeException("Telegram error: " + error.message));
                return;
            }

            if (!(result instanceof TdApi.Chats chats) || chats.chatIds.length == 0) {
                future.complete(0L);
                return;
            }

            List<CompletableFuture<Long>> futures = new ArrayList<>();
            for (long chatId : chats.chatIds) {
                futures.add(getChatIfInternalIndex(chatId).exceptionally(ex -> 0L));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                    .map(CompletableFuture::join)
                    .filter(foundChatId -> foundChatId != null && foundChatId != 0L)
                    .findFirst()
                    .orElse(0L))
                .whenComplete((chatId, error) -> {
                    if (error != null) {
                        future.completeExceptionally(error);
                    } else {
                        future.complete(chatId);
                    }
                });
        });

        return future;
    }

    private CompletableFuture<Long> getChatIfInternalIndex(long chatId) {
        CompletableFuture<Long> future = new CompletableFuture<>();

        client.send(new TdApi.GetChat(chatId), result -> {
            if (result instanceof TdApi.Chat chat && chat.type instanceof TdApi.ChatTypeSupergroup supergroupType && supergroupType.isChannel) {
                if (isInternalIndexChat(chat.title)) {
                    future.complete(chat.id);
                    return;
                }
            }
            future.complete(0L);
        });

        return future;
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
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (client == null) {
            future.completeExceptionally(new IllegalStateException("Telegram client not initialized"));
            return future;
        }

        if (!isAuthorized) {
            future.completeExceptionally(new IllegalStateException(
                "Unauthorized. Telegram requires authentication. " +
                "Use the TDLib CLI client to authenticate first."
            ));
            return future;
        }

        // First leave the chat
        client.send(new TdApi.LeaveChat(chatId), leaveResult -> {
            if (leaveResult instanceof TdApi.Error error) {
                logger.warn("Error leaving chat (it may no longer be a member): {}", error.message);
                // Continue trying to delete the chat locally
            }

            // Then delete the chat locally
            client.send(new TdApi.DeleteChat(chatId), deleteResult -> {
                if (deleteResult instanceof TdApi.Error error) {
                    logger.error("Error deleting folder: {}", error.message);
                    future.completeExceptionally(new RuntimeException("Telegram error: " + error.message));
                    return;
                }

                logger.info("Folder deleted: ID {}", chatId);
                future.complete(null);
            });
        });

        return future;
    }

    // ==================== STATISTICS METHODS ====================

    /**
     * Gets quick TDLib local storage statistics.
     */
    @Override
    public CompletableFuture<StorageStatsResponse> getStorageStatisticsFast() {
        CompletableFuture<StorageStatsResponse> future = new CompletableFuture<>();

        if (client == null) {
            future.completeExceptionally(new IllegalStateException("Telegram client not initialized"));
            return future;
        }
        if (!isAuthorized) {
            future.completeExceptionally(new IllegalStateException("Not authorized in Telegram"));
            return future;
        }

        client.send(new TdApi.GetStorageStatisticsFast(), result -> {
            if (result instanceof TdApi.StorageStatisticsFast stats) {
                future.complete(new StorageStatsResponse(
                    stats.filesSize,
                    stats.fileCount,
                    stats.databaseSize,
                    stats.languagePackDatabaseSize,
                    stats.logSize
                ));
            } else if (result instanceof TdApi.Error error) {
                logger.error("Error getting storage stats [{}]: {}", error.code, error.message);
                future.completeExceptionally(new RuntimeException(
                    "TDLib error %d: %s".formatted(error.code, error.message)));
            }
        });

        return future.orTimeout(ServiceDefaults.TDLIB_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Gets TDLib network usage statistics.
     *
     * @param onlyCurrent if true, returns only data since the current TDLib launch
     */
    @Override
    public CompletableFuture<NetworkStatsResponse> getNetworkStatistics(boolean onlyCurrent) {
        CompletableFuture<NetworkStatsResponse> future = new CompletableFuture<>();

        if (client == null) {
            future.completeExceptionally(new IllegalStateException("Telegram client not initialized"));
            return future;
        }
        if (!isAuthorized) {
            future.completeExceptionally(new IllegalStateException("Not authorized in Telegram"));
            return future;
        }

        client.send(new TdApi.GetNetworkStatistics(onlyCurrent), result -> {
            if (result instanceof TdApi.NetworkStatistics stats) {
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

                future.complete(new NetworkStatsResponse(stats.sinceDate, fileEntries, callEntries));
            } else if (result instanceof TdApi.Error error) {
                logger.error("Error getting network stats [{}]: {}", error.code, error.message);
                future.completeExceptionally(new RuntimeException(
                    "TDLib error %d: %s".formatted(error.code, error.message)));
            }
        });

        return future.orTimeout(ServiceDefaults.TDLIB_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Gets the configured limits for the Telegram account.
     */
    @Override
    public CompletableFuture<TelegramLimitsResponse> getTelegramLimits() {
        if (client == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Telegram client not initialized"));
        }
        if (!isAuthorized) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not authorized in Telegram"));
        }

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
            ))
            .orTimeout(ServiceDefaults.TDLIB_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private CompletableFuture<Long> getOptionLong(String name) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        client.send(new TdApi.GetOption(name), result -> {
            if (result instanceof TdApi.OptionValueInteger opt) {
                future.complete(opt.value);
            } else if (result instanceof TdApi.OptionValueEmpty) {
                future.complete(0L);
            } else if (result instanceof TdApi.Error error) {
                future.completeExceptionally(new RuntimeException(error.message));
            } else {
                future.complete(0L);
            }
        });
        return future;
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
