/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.zademy.nekolu.dto.BulkDeleteRequest;
import com.zademy.nekolu.dto.BulkDeleteResponse;
import com.zademy.nekolu.dto.DeleteMessageResponse;
import com.zademy.nekolu.dto.DownloadResponse;
import com.zademy.nekolu.dto.FileActionResponse;
import com.zademy.nekolu.dto.FileInfoResponse;
import com.zademy.nekolu.dto.UploadResponse;
import com.zademy.nekolu.model.TelegramFileMessage;
import com.zademy.nekolu.model.TelegramFileState;
import com.zademy.nekolu.service.FakeTelegramService;
import com.zademy.nekolu.service.FakeTelegramService.SessionState;

/**
 * Unit tests for the file-management module through the TelegramService seam,
 * using the in-memory adapter. No Spring context, no TDLib, no native
 * library: the search behaviour is exercised end to end through the seam
 * interface, the way callers and production code consume it.
 */
class FileServiceImplTest {

    private FakeTelegramService telegram;
    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        telegram = new FakeTelegramService();
        fileService = new FileServiceImpl(
            telegram,
            new MetadataIndexServiceImpl(),
            Caffeine.newBuilder().build());
    }

    @Test
    void searchFilesByTypeReturnsOnlyFilesOfThatType() throws Exception {
        telegram.withMessages(
            photo(1, 1, 100, "one.jpg", 1000, 10),
            video(2, 1, 200, "two.mp4", 2000, 20),
            document(3, 1, 300, "three.pdf", 3000, 30));

        List<FileInfoResponse> result = fileService.searchFilesByType("video", 10, "").get();

        assertEquals(1, result.size());
        assertEquals("two.mp4", result.get(0).fileName());
    }

    @Test
    void searchFilesInChatFiltersByRequestedType() throws Exception {
        telegram.withMessages(
            photo(1, 10, 100, "one.jpg", 1000, 10),
            video(2, 10, 200, "two.mp4", 2000, 20),
            photo(3, 99, 900, "other-chat.jpg", 1000, 5));

        List<FileInfoResponse> result = fileService.searchFilesInChat(10, "photo", 10).get();

        assertEquals(1, result.size());
        assertEquals("one.jpg", result.get(0).fileName());
    }

    @Test
    void searchFilesAdvancedCombinesOwnChatAndGlobalSearch() throws Exception {
        // own chat (1): two files; another chat (99): one file
        telegram.withMessages(
            photo(1, 1, 100, "own-photo.jpg", 1000, 100),
            video(2, 1, 200, "own-video.mp4", 2000, 200),
            document(3, 99, 300, "global-doc.pdf", 3000, 300));

        List<FileInfoResponse> result = fileService
            .searchFilesAdvanced(null, 10, null, null, null, null, null, null, null, null)
            .get();

        assertEquals(3, result.size());
        // newest first by default
        assertEquals("global-doc.pdf", result.get(0).fileName());
        assertEquals("own-video.mp4", result.get(1).fileName());
        assertEquals("own-photo.jpg", result.get(2).fileName());
    }

    @Test
    void searchFilesAdvancedAppliesSizeAndNameFilters() throws Exception {
        telegram.withMessages(
            photo(1, 1, 100, "own-photo.jpg", 1000, 100),
            video(2, 1, 200, "own-video.mp4", 2000, 200),
            document(3, 99, 300, "global-doc.pdf", 3000, 300));

        List<FileInfoResponse> bySize = fileService
            .searchFilesAdvanced(null, 10, null, null, null, null, 1500L, null, null, null)
            .get();
        assertEquals(2, bySize.size());

        List<FileInfoResponse> byName = fileService
            .searchFilesAdvanced(null, 10, null, null, null, null, null, null, null, "VIDEO")
            .get();
        assertEquals(1, byName.size());
        assertEquals("own-video.mp4", byName.get(0).fileName());
    }

    @Test
    void getRecentFilesFromOwnChatReturnsOwnChatMessages() throws Exception {
        telegram
            .withOwnChatId(42)
            .withMessages(
                photo(1, 42, 100, "mine.jpg", 1000, 10),
                photo(2, 7, 700, "theirs.jpg", 1000, 20));

        List<FileInfoResponse> result = fileService.getRecentFilesFromOwnChat(10).get();

        assertEquals(1, result.size());
        assertEquals("mine.jpg", result.get(0).fileName());
    }

    @Test
    void searchInChatFailsSilentlyWhenSessionIsUnauthorized() throws Exception {
        // Preserved behaviour: an unauthorized session yields an empty list,
        // not an error, for in-chat search.
        telegram.inState(SessionState.UNAUTHORIZED);

        List<FileInfoResponse> result = fileService.searchFilesInChat(10, "photo", 10).get();

        assertTrue(result.isEmpty());
    }

    @Test
    void searchByTypeFailsSilentlyWhenSessionIsUnauthorized() throws Exception {
        // Unification decision: session errors during search surface as an
        // empty list (the dominant existing path), instead of the old mix of
        // thrown IllegalStateException and silent empty results.
        telegram.inState(SessionState.UNAUTHORIZED);

        List<FileInfoResponse> result = fileService.searchFilesByType("photo", 10, "").get();

        assertTrue(result.isEmpty());
    }

    @Test
    void downloadFileReturnsCompletedWhenAlreadyDownloaded() throws Exception {
        telegram.withFileState(new TelegramFileState(500, 2048, false, 2048, "/tmp/done.bin", true));

        DownloadResponse response = fileService.downloadFile(500).get();

        assertEquals(DownloadResponse.STATUS_COMPLETED, response.status());
        assertEquals("/tmp/done.bin", response.localPath());
        assertEquals(100, response.progress());
    }

    @Test
    void downloadFileReturnsPendingAndStartsDownloadThroughSeam() throws Exception {
        telegram.withFileState(new TelegramFileState(600, 2048, false, 512, null, false));

        DownloadResponse response = fileService.downloadFile(600).get();

        assertEquals(DownloadResponse.STATUS_PENDING, response.status());
        assertEquals("Download started in background. Check GET /{fileId} for progress.", response.message());
    }

    @Test
    void downloadFileWaitsForUploadReleaseBeforeStarting() throws Exception {
        // Upload a file through the seam first: the fake registers the staged
        // source as tracked. Downloading must consume the release.
        telegram
            .withUploadResult(message(77, 1, 700, "staged.pdf", 4096, "document", 1700000000))
            .withFileState(new TelegramFileState(700, 4096, false, 0, null, false));
        telegram.sendDocument(1, "/staging/staged.pdf", null).get();
        assertTrue(telegram.isUploadTracked(700));

        DownloadResponse response = fileService.downloadFile(700).get();

        assertEquals(DownloadResponse.STATUS_PENDING, response.status());
        // The release was consumed: the staged source is no longer tracked
        assertTrue(telegram.trackedUploads().isEmpty());
    }

    @Test
    void downloadFileReportsFailureWhenFileIsUnknown() throws Exception {
        DownloadResponse response = fileService.downloadFile(404).get();

        assertEquals(DownloadResponse.STATUS_FAILED, response.status());
        assertTrue(response.message().contains("File not found"));
    }

    @Test
    void downloadFilesReportsEachFileIndividually() throws Exception {
        telegram.withFileState(new TelegramFileState(500, 2048, false, 2048, "/tmp/done.bin", true));

        List<DownloadResponse> responses = fileService.downloadFiles(List.of(500L, 404L)).get();

        assertEquals(2, responses.size());
        assertEquals(DownloadResponse.STATUS_COMPLETED, responses.get(0).status());
        assertEquals(DownloadResponse.STATUS_FAILED, responses.get(1).status());
    }

    @Test
    void getFileInfoMapsSeamStateIntoResponse() throws Exception {
        telegram.withFileState(new TelegramFileState(500, 2048, false, 2048, "/tmp/done.bin", true));

        FileInfoResponse info = fileService.getFileInfo(500).get();

        assertTrue(info.isDownloaded());
        assertEquals("/tmp/done.bin", info.localPath());
        assertEquals(2048, info.fileSize());
    }

    @Test
    void uploadFileReturnsCompletedResponse(@TempDir File tempDir) throws Exception {
        File staged = Files.writeString(tempDir.toPath().resolve("report.pdf"), "payload").toFile();
        telegram.withUploadResult(message(77, 1, 700, null, 0, "document", 1700000000));

        UploadResponse response = fileService.uploadFile(staged, 1, "caption").get();

        assertEquals(UploadResponse.STATUS_COMPLETED, response.status());
        assertEquals(77, response.messageId());
        assertEquals("report.pdf", response.fileName());
        assertEquals("payload".length(), response.fileSize());
        assertEquals("telegram-700", response.logicalFileId());
        assertTrue(telegram.isUploadTracked(700));
    }

    @Test
    void uploadFileFailsForMissingLocalFile() {
        telegram.withUploadResult(message(77, 1, 700, "x.pdf", 10, "document", 1700000000));

        ExecutionException thrown = assertThrows(ExecutionException.class,
            () -> fileService.uploadFile(new File("/nonexistent/report.pdf"), 1, null).get());

        assertTrue(thrown.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void deleteMessageSucceedsAndResolvesFileThroughSeam() throws Exception {
        telegram.withMessages(message(55, 10, 550, "gone.pdf", 100, "document", 1700000000));

        DeleteMessageResponse response = fileService.deleteMessage(55, 10, true).get();

        assertEquals(DeleteMessageResponse.STATUS_SUCCESS, response.status());
        assertEquals("Message deleted permanently", response.message());
        assertEquals(List.of("10:55:true"), telegram.deletedMessages());
    }

    @Test
    void deleteMessageReportsFailureWhenSeamFails() throws Exception {
        telegram.failingWith(new RuntimeException("Telegram error 400: MESSAGE_ID_INVALID"));

        DeleteMessageResponse response = fileService.deleteMessage(55, 10, false).get();

        assertEquals(DeleteMessageResponse.STATUS_FAILED, response.status());
        assertTrue(response.message().startsWith("Error deleting message:"));
    }

    @Test
    void bulkDeleteCountsSuccessesAndFailures() throws Exception {
        telegram
            .withMessages(message(1, 10, 100, "a.pdf", 10, "document", 1700000000))
            .withMessages(message(2, 10, 200, "b.pdf", 10, "document", 1700000001));

        BulkDeleteResponse response = fileService.bulkDeleteMessages(new BulkDeleteRequest(
            List.of(
                new BulkDeleteRequest.DeleteItem(1, 10, 100, "a.pdf"),
                new BulkDeleteRequest.DeleteItem(2, 10, 200, "b.pdf")),
            false)).get();

        assertEquals(2, response.totalRequested());
        assertEquals(2, response.successCount());
        assertEquals(0, response.failedCount());
    }

    @Test
    void logicalOperationsRemainDisabledByDesign() throws Exception {
        // Characterization tests: the logical drive is not implemented. These
        // operations must keep failing explicitly (and trash stay empty)
        // rather than pretending to work.
        assertEquals(FileActionResponse.STATUS_FAILED, fileService.restoreFile(1).get().status());
        assertEquals(FileActionResponse.STATUS_FAILED, fileService.moveFile(1, "/x").get().status());
        assertEquals(FileActionResponse.STATUS_FAILED, fileService.archiveFile(1, true).get().status());
        assertTrue(fileService.listTrash().get().isEmpty());
    }

    // ==================== HELPERS ====================

    private static TelegramFileMessage photo(long messageId, long chatId, long fileId, String fileName, long size, int date) {
        return message(messageId, chatId, fileId, fileName, size, "photo", date);
    }

    private static TelegramFileMessage video(long messageId, long chatId, long fileId, String fileName, long size, int date) {
        return message(messageId, chatId, fileId, fileName, size, "video", date);
    }

    private static TelegramFileMessage document(long messageId, long chatId, long fileId, String fileName, long size, int date) {
        return message(messageId, chatId, fileId, fileName, size, "document", date);
    }

    private static TelegramFileMessage message(long messageId, long chatId, long fileId, String fileName, long size, String type, int date) {
        return new TelegramFileMessage(
            messageId, chatId, fileId, fileName, size, null, type,
            null, null, null, null, date, false, null);
    }
}
