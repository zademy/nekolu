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
import com.zademy.nekolu.constants.MediaConstants;
import com.zademy.nekolu.constants.ServiceDefaults;
import com.zademy.nekolu.config.TelegramConfig;
import com.zademy.nekolu.dto.FolderInfo;
import com.zademy.nekolu.exception.Exceptions;
import com.zademy.nekolu.exception.TelegramNotFoundException;
import com.zademy.nekolu.exception.TelegramOperationException;
import com.zademy.nekolu.dto.NetworkStatsResponse;
import com.zademy.nekolu.dto.StorageStatsResponse;
import com.zademy.nekolu.dto.TelegramLimitsResponse;
import com.zademy.nekolu.model.TelegramFileMessage;
import com.zademy.nekolu.model.TelegramFileState;
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
    private volatile String authState = AUTH_STATE_WAIT_PHONE_NUMBER;
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
                    target.completeExceptionally(new TelegramOperationException(error.code, error.message));
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
        return Exceptions.unwrap(error);
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

    // ==================== FIRST-RUN AUTHENTICATION ====================

    @Override
    public String getAuthState() {
        return authState;
    }

    /**
     * Authentication submissions travel through the unguarded path: the
     * readiness preconditions do not apply before a session exists, while
     * the request timeout and typed error translation still do.
     */
    @Override
    public CompletableFuture<Void> submitPhoneNumber(String phoneNumber) {
        TdApi.SetAuthenticationPhoneNumber request = new TdApi.SetAuthenticationPhoneNumber();
        request.phoneNumber = phoneNumber;
        request.settings = new TdApi.PhoneNumberAuthenticationSettings();
        request.settings.allowFlashCall = false;
        request.settings.allowMissedCall = false;
        request.settings.isCurrentPhoneNumber = false;
        request.settings.allowSmsRetrieverApi = false;

        return sendUnguarded(request).<Void>thenApply(_ok -> null);
    }

    @Override
    public CompletableFuture<Void> submitAuthCode(String code) {
        return sendUnguarded(new TdApi.CheckAuthenticationCode(code)).<Void>thenApply(_ok -> null);
    }

    @Override
    public CompletableFuture<Void> submitAuthPassword(String password) {
        return sendUnguarded(new TdApi.CheckAuthenticationPassword(password)).<Void>thenApply(_ok -> null);
    }

    // ==================== FILE MESSAGE OPERATIONS (DOMAIN TYPES) ====================

    @Override
    public CompletableFuture<TelegramFileState> startDownload(long fileId) {
        TdApi.DownloadFile download = new TdApi.DownloadFile();
        download.fileId = (int) fileId;
        download.priority = 1;
        download.offset = 0;
        download.limit = 0;
        download.synchronous = false;

        // Non-blocking by contract: the initial response carries the state as
        // the download begins; completion is observed via getFileState.
        return send(download).thenApply(this::toFileState);
    }

    @Override
    public CompletableFuture<TelegramFileState> getFileState(long fileId) {
        TdApi.GetFile getFile = new TdApi.GetFile();
        getFile.fileId = (int) fileId;

        // GetFile is a local TDLib lookup (no network round-trip in the
        // common case) and list-refresh fans out one per file; rate-limiting
        // that fan-out starved the limiter and timed out whole listings.
        return sendUnguarded(getFile).thenApply(this::toFileState);
    }

    private TelegramFileState toFileState(TdApi.File file) {
        return new TelegramFileState(
            file.id,
            file.size,
            file.local != null && file.local.isDownloadingActive,
            file.local != null ? file.local.downloadedPrefixSize : 0,
            file.local != null ? file.local.path : null,
            isFileActuallyDownloaded(file)
        );
    }

    @Override
    public CompletableFuture<TelegramFileMessage> sendDocument(long chatId, String filePath, String caption) {
        TdApi.InputMessageDocument document = new TdApi.InputMessageDocument();
        document.document = new TdApi.InputFileLocal(filePath);
        document.thumbnail = null;
        document.caption = caption != null ? new TdApi.FormattedText(caption, null) : null;

        TdApi.SendMessage sendMessage = new TdApi.SendMessage();
        sendMessage.chatId = chatId;
        sendMessage.inputMessageContent = document;

        return send(sendMessage).thenCompose(message -> trackedFileMessage(message, filePath));
    }

    @Override
    public CompletableFuture<TelegramFileMessage> sendPhoto(long chatId, String filePath, String caption) {
        TdApi.InputMessagePhoto photo = new TdApi.InputMessagePhoto();
        photo.photo = new TdApi.InputFileLocal(filePath);
        photo.thumbnail = null;
        photo.caption = caption != null ? new TdApi.FormattedText(caption, null) : null;
        photo.hasSpoiler = false;

        TdApi.SendMessage sendMessage = new TdApi.SendMessage();
        sendMessage.chatId = chatId;
        sendMessage.inputMessageContent = photo;

        return send(sendMessage).thenCompose(message -> trackedFileMessage(message, filePath));
    }

    /**
     * Registers the staged local source for upload tracking and maps the
     * created message to the domain view. Guards against the
     * upload-completion update racing ahead of registration: if the transfer
     * already finished, the staged source is cleaned eagerly instead of
     * leaving an orphan entry nobody completes. A nanosecond-scale window
     * remains between this check and the registration; its worst case (a
     * leftover staged file plus an orphan release entry) is bounded by the
     * staging area's startup purge.
     */
    private CompletableFuture<TelegramFileMessage> trackedFileMessage(TdApi.Message message, String stagedFilePath) {
        TdApi.File file = fileOf(message);
        TelegramFileMessage fileMessage = toFileMessage(message);
        if (file == null || fileMessage == null) {
            return CompletableFuture.failedFuture(new TelegramNotFoundException("Message contains no file"));
        }
        if (file.remote != null && !file.remote.isUploadingActive && file.remote.isUploadingCompleted) {
            java.io.File staged = new java.io.File(stagedFilePath);
            if (staged.exists() && staged.delete()) {
                logger.info("[Upload] Upload already completed before tracking; staged file cleaned eagerly: {}", stagedFilePath);
            }
            return CompletableFuture.completedFuture(fileMessage);
        }
        trackUpload(fileMessage.fileId(), stagedFilePath);
        return CompletableFuture.completedFuture(fileMessage);
    }

    /**
     * Extracts the TDLib file carried by a message, or null when it carries
     * none.
     */
    private TdApi.File fileOf(TdApi.Message message) {
        if (message.content == null) return null;
        return switch (message.content) {
            case TdApi.MessagePhoto photo when photo.photo != null && photo.photo.sizes.length > 0 ->
                photo.photo.sizes[photo.photo.sizes.length - 1].photo;
            case TdApi.MessageVideo video when video.video != null -> video.video.video;
            case TdApi.MessageAudio audio when audio.audio != null -> audio.audio.audio;
            case TdApi.MessageDocument doc when doc.document != null -> doc.document.document;
            case TdApi.MessageVoiceNote voice when voice.voiceNote != null -> voice.voiceNote.voice;
            case TdApi.MessageVideoNote videoNote when videoNote.videoNote != null -> videoNote.videoNote.video;
            default -> null;
        };
    }

    @Override
    public CompletableFuture<Void> deleteMessages(long chatId, List<Long> messageIds, boolean revoke) {
        TdApi.DeleteMessages deleteMessages = new TdApi.DeleteMessages();
        deleteMessages.chatId = chatId;
        deleteMessages.messageIds = messageIds.stream().mapToLong(Long::longValue).toArray();
        deleteMessages.revoke = revoke;

        return send(deleteMessages).<Void>thenApply(_ok -> null);
    }

    @Override
    public CompletableFuture<Void> deleteLocalFile(long fileId) {
        TdApi.DeleteFile deleteFile = new TdApi.DeleteFile();
        deleteFile.fileId = (int) fileId;

        return send(deleteFile).<Void>thenApply(_ok -> null);
    }


    @Override
    public CompletableFuture<List<TelegramFileMessage>> getFileMessages(long chatId, long fromMessageId, int limit) {
        TdApi.GetChatHistory getHistory = new TdApi.GetChatHistory();
        getHistory.chatId = chatId;
        getHistory.fromMessageId = fromMessageId;
        getHistory.offset = 0;
        getHistory.limit = limit;
        getHistory.onlyLocal = false;

        return send(getHistory).thenApply(messages -> toFileMessages(messages.messages));
    }

    @Override
    public CompletableFuture<List<TelegramFileMessage>> searchFileMessages(String query, String type, String offset, int limit) {
        TdApi.SearchMessages search = new TdApi.SearchMessages();
        search.chatList = null;
        search.query = query != null ? query : "";
        search.offset = offset != null ? offset : "";
        search.limit = limit;
        search.filter = createSearchFilterForType(type);
        search.minDate = 0;
        search.maxDate = 0;

        return send(search).thenApply(found -> toFileMessages(found.messages));
    }

    @Override
    public CompletableFuture<TelegramFileMessage> getFileMessage(long chatId, long messageId) {
        TdApi.GetMessage getMessage = new TdApi.GetMessage();
        getMessage.chatId = chatId;
        getMessage.messageId = messageId;

        return send(getMessage).thenCompose(message -> {
            TelegramFileMessage fileMessage = toFileMessage(message);
            if (fileMessage == null) {
                return CompletableFuture.failedFuture(new TelegramNotFoundException("Message contains no file"));
            }
            return CompletableFuture.completedFuture(fileMessage);
        });
    }

    private List<TelegramFileMessage> toFileMessages(TdApi.Message[] messages) {
        List<TelegramFileMessage> fileMessages = new ArrayList<>();
        for (TdApi.Message message : messages) {
            TelegramFileMessage fileMessage = toFileMessage(message);
            if (fileMessage != null) {
                fileMessages.add(fileMessage);
            }
        }
        return fileMessages;
    }

    /**
     * Maps a Telegram message to the domain file-message view, or null when
     * the message carries no file. Single source of the message-content
     * classification.
     */
    private TelegramFileMessage toFileMessage(TdApi.Message message) {
        if (message.content == null) return null;

        return switch (message.content) {
            case TdApi.MessagePhoto photo -> fromPhoto(message, photo);
            case TdApi.MessageVideo video -> fromVideo(message, video);
            case TdApi.MessageAudio audio -> fromAudio(message, audio);
            case TdApi.MessageDocument doc -> fromDocument(message, doc);
            case TdApi.MessageVoiceNote voice -> fromVoiceNote(message, voice);
            case TdApi.MessageVideoNote videoNote -> fromVideoNote(message, videoNote);
            default -> null;
        };
    }

    private TelegramFileMessage fromPhoto(TdApi.Message message, TdApi.MessagePhoto photo) {
        if (photo.photo == null || photo.photo.sizes == null || photo.photo.sizes.length == 0) {
            return null;
        }
        // Use the largest size for the main file
        TdApi.PhotoSize largest = photo.photo.sizes[photo.photo.sizes.length - 1];
        TdApi.File file = largest.photo;

        // Use the smallest size for the thumbnail
        TdApi.PhotoSize smallest = photo.photo.sizes[0];
        String thumbnailPath = thumbnailPathOf(smallest.photo);

        return new TelegramFileMessage(
            message.id,
            message.chatId,
            file.id,
            MediaConstants.FILE_PREFIX_PHOTO + file.id + MediaConstants.EXTENSION_JPG,
            file.size,
            MediaConstants.MIME_IMAGE_JPEG,
            FileTypeConstants.PHOTO,
            largest.width,
            largest.height,
            null,
            thumbnailPath,
            message.date,
            isFileActuallyDownloaded(file),
            file.local != null ? file.local.path : null
        );
    }

    private TelegramFileMessage fromVideo(TdApi.Message message, TdApi.MessageVideo video) {
        if (video.video == null) return null;
        TdApi.File file = video.video.video;

        String thumbnailPath = null;
        if (video.video.thumbnail != null && video.video.thumbnail.file != null) {
            thumbnailPath = thumbnailPathOf(video.video.thumbnail.file);
        }

        return new TelegramFileMessage(
            message.id,
            message.chatId,
            file.id,
            video.video.fileName != null ? video.video.fileName
                : MediaConstants.FILE_PREFIX_VIDEO + file.id + MediaConstants.EXTENSION_MP4,
            file.size,
            video.video.mimeType != null ? video.video.mimeType : MediaConstants.MIME_VIDEO_MP4,
            FileTypeConstants.VIDEO,
            video.video.width,
            video.video.height,
            video.video.duration,
            thumbnailPath,
            message.date,
            isFileActuallyDownloaded(file),
            file.local != null ? file.local.path : null
        );
    }

    private TelegramFileMessage fromAudio(TdApi.Message message, TdApi.MessageAudio audio) {
        if (audio.audio == null) return null;
        TdApi.File file = audio.audio.audio;

        return new TelegramFileMessage(
            message.id,
            message.chatId,
            file.id,
            audio.audio.fileName != null ? audio.audio.fileName
                : MediaConstants.FILE_PREFIX_AUDIO + file.id + MediaConstants.EXTENSION_MP3,
            file.size,
            audio.audio.mimeType != null ? audio.audio.mimeType : MediaConstants.MIME_AUDIO_MPEG,
            FileTypeConstants.AUDIO,
            null,
            null,
            audio.audio.duration,
            null,
            message.date,
            isFileActuallyDownloaded(file),
            file.local != null ? file.local.path : null
        );
    }

    private TelegramFileMessage fromDocument(TdApi.Message message, TdApi.MessageDocument doc) {
        if (doc.document == null) return null;
        TdApi.File file = doc.document.document;

        return new TelegramFileMessage(
            message.id,
            message.chatId,
            file.id,
            doc.document.fileName != null ? doc.document.fileName
                : MediaConstants.FILE_PREFIX_DOCUMENT + file.id,
            file.size,
            doc.document.mimeType != null ? doc.document.mimeType : MediaConstants.MIME_APPLICATION_OCTET_STREAM,
            FileTypeConstants.DOCUMENT,
            null,
            null,
            null,
            null,
            message.date,
            isFileActuallyDownloaded(file),
            file.local != null ? file.local.path : null
        );
    }

    private TelegramFileMessage fromVoiceNote(TdApi.Message message, TdApi.MessageVoiceNote voice) {
        if (voice.voiceNote == null) return null;
        TdApi.File file = voice.voiceNote.voice;

        return new TelegramFileMessage(
            message.id,
            message.chatId,
            file.id,
            MediaConstants.FILE_PREFIX_VOICE + file.id + MediaConstants.EXTENSION_OGA,
            file.size,
            MediaConstants.MIME_AUDIO_OGG,
            FileTypeConstants.VOICE,
            null,
            null,
            voice.voiceNote.duration,
            null,
            message.date,
            isFileActuallyDownloaded(file),
            file.local != null ? file.local.path : null
        );
    }

    private TelegramFileMessage fromVideoNote(TdApi.Message message, TdApi.MessageVideoNote videoNote) {
        if (videoNote.videoNote == null) return null;
        TdApi.File file = videoNote.videoNote.video;

        return new TelegramFileMessage(
            message.id,
            message.chatId,
            file.id,
            MediaConstants.FILE_PREFIX_VIDEO_NOTE + file.id + MediaConstants.EXTENSION_MP4,
            file.size,
            MediaConstants.MIME_VIDEO_MP4,
            FileTypeConstants.VIDEO_NOTE,
            videoNote.videoNote.length,
            videoNote.videoNote.length,
            videoNote.videoNote.duration,
            null,
            message.date,
            isFileActuallyDownloaded(file),
            file.local != null ? file.local.path : null
        );
    }

    /**
     * Returns the thumbnail path when it is already available locally; never
     * forces a download.
     */
    private String thumbnailPathOf(TdApi.File thumbnailFile) {
        if (thumbnailFile == null || thumbnailFile.local == null) {
            return null;
        }
        if (thumbnailFile.local.isDownloadingCompleted && thumbnailFile.local.path != null
                && !thumbnailFile.local.path.isBlank()) {
            return thumbnailFile.local.path;
        }
        return null;
    }

    private boolean isFileActuallyDownloaded(TdApi.File file) {
        if (isUploadTracked(file.id)) {
            return false;
        }
        if (file.local == null || !file.local.isDownloadingCompleted) {
            return false;
        }
        if (file.local.path == null || file.local.path.isBlank()) {
            return false;
        }
        return new java.io.File(file.local.path).exists();
    }

    /**
     * Single source of the workspace-type-to-TDLib-filter mapping.
     */
    private TdApi.SearchMessagesFilter createSearchFilterForType(String type) {
        if (type == null || type.isBlank() || FileTypeConstants.ALL.equalsIgnoreCase(type)) return null;

        return switch (type.toLowerCase()) {
            case FileTypeConstants.PHOTO -> new TdApi.SearchMessagesFilterPhoto();
            case FileTypeConstants.VIDEO -> new TdApi.SearchMessagesFilterVideo();
            case FileTypeConstants.AUDIO -> new TdApi.SearchMessagesFilterAudio();
            case FileTypeConstants.DOCUMENT -> new TdApi.SearchMessagesFilterDocument();
            case FileTypeConstants.VOICE -> new TdApi.SearchMessagesFilterVoiceNote();
            case FileTypeConstants.VIDEO_NOTE -> new TdApi.SearchMessagesFilterVideoNote();
            default -> null; // No filter = all
        };
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

        // The owner's user id comes from configuration when present and is
        // resolved through TDLib otherwise — no manual setup required.
        CompletableFuture<Long> userIdFuture = telegramConfig.getUserId() != 0
            ? CompletableFuture.completedFuture(telegramConfig.getUserId())
            : send(new TdApi.GetMe()).thenApply(me -> me.id);

        userIdFuture
            .thenCompose(userId -> {
                TdApi.CreatePrivateChat createChat = new TdApi.CreatePrivateChat();
                createChat.userId = userId;
                createChat.force = false;
                return send(createChat);
            })
            .whenComplete((chat, error) -> {
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
    public void trackUpload(long fileId, String tempFilePath) {
        pendingUploads.put((int) fileId, tempFilePath);
    }

    @Override
    public boolean isUploadTracked(long fileId) {
        return pendingUploads.containsKey((int) fileId);
    }

    @Override
    public CompletableFuture<Void> waitForUploadRelease(long fileId) {
        int id = (int) fileId;
        if (!pendingUploads.containsKey(id)) {
            return CompletableFuture.completedFuture(null);
        }
        return pendingUploadReleases.computeIfAbsent(id, _ignored -> new CompletableFuture<>());
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
    }

    private void handleAuthorizationState(TdApi.AuthorizationState state) {
        if (state instanceof TdApi.AuthorizationStateReady) {
            authState = AUTH_STATE_READY;
            isAuthorized = true;
            logger.info("TDLib ready and authorized");
            // Run off the TDLib callback thread: the cleanup performs
            // rate-limited requests that must never stall update delivery.
            CompletableFuture.runAsync(this::cleanupLegacyInternalIndexChat);
        } else if (state instanceof TdApi.AuthorizationStateWaitTdlibParameters) {
            logger.info("Waiting for TDLib parameters...");
        } else if (state instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            authState = AUTH_STATE_WAIT_PHONE_NUMBER;
            logger.info("Waiting for the phone number — first-run wizard available at /setup");
        } else if (state instanceof TdApi.AuthorizationStateWaitCode) {
            authState = AUTH_STATE_WAIT_CODE;
            logger.info("Waiting for verification code...");
        } else if (state instanceof TdApi.AuthorizationStateWaitPassword) {
            authState = AUTH_STATE_WAIT_PASSWORD;
            logger.info("Waiting for the two-step-verification password...");
        } else if (state instanceof TdApi.AuthorizationStateWaitRegistration) {
            logger.warn("TDLib asks for account registration (new account); the wizard does not cover this — use the TDLib Example client");
        } else if (state instanceof TdApi.AuthorizationStateWaitOtherDeviceConfirmation) {
            logger.warn("TDLib waits for confirmation on another device...");
        } else if (state instanceof TdApi.AuthorizationStateClosed) {
            isAuthorized = false;
        }
    }

    /**
     * Creates a "folder", internally represented as a private Telegram channel.
     */
    @Override
    public CompletableFuture<FolderInfo> createFolder(String title, String description) {
        TdApi.CreateNewSupergroupChat request = new TdApi.CreateNewSupergroupChat();
        request.title = title;
        request.isChannel = true;
        request.isForum = false;
        request.description = description != null ? description : "";
        request.messageAutoDeleteTime = 0;
        request.forImport = false;

        return send(request).thenApply(chat -> {
            logger.info("Folder created: {} (ID: {})", chat.title, chat.id);
            return new FolderInfo(chat.id, chat.title, description != null ? description : "", 1, 0);
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
