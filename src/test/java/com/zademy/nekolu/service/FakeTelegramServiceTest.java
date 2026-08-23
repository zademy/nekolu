/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

import com.zademy.nekolu.exception.TelegramNotInitializedException;
import com.zademy.nekolu.exception.TelegramOperationException;
import com.zademy.nekolu.exception.TelegramUnauthorizedException;
import com.zademy.nekolu.model.TelegramFileMessage;
import com.zademy.nekolu.model.TelegramFileState;
import com.zademy.nekolu.service.FakeTelegramService.SessionState;

/**
 * Contract tests for the file-message operations of the TelegramService seam,
 * expressed through the in-memory adapter. These document the behaviour the
 * TDLib-backed adapter must satisfy: result shapes plus the uniform error
 * modes for uninitialized, unauthorized, and failed sessions.
 */
class FakeTelegramServiceTest {

    @Test
    void getFileMessagesReturnsProgrammedMessagesForTheChat() throws Exception {
        FakeTelegramService telegram = new FakeTelegramService()
            .withMessages(photo(1, 10), video(2, 10), photo(3, 99));

        List<TelegramFileMessage> result = telegram.getFileMessages(10, 0, 10).get();

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(message -> message.chatId() == 10));
    }

    @Test
    void searchFileMessagesFiltersByType() throws Exception {
        FakeTelegramService telegram = new FakeTelegramService()
            .withMessages(photo(1, 10), video(2, 10), document(3, 10));

        List<TelegramFileMessage> result = telegram.searchFileMessages("", "video", "", 10).get();

        assertEquals(1, result.size());
        assertEquals("video", result.get(0).type());
    }

    @Test
    void searchFileMessagesAllTypeReturnsEverything() throws Exception {
        FakeTelegramService telegram = new FakeTelegramService()
            .withMessages(photo(1, 10), video(2, 10), document(3, 10));

        assertEquals(3, telegram.searchFileMessages("", "all", "", 10).get().size());
        assertEquals(3, telegram.searchFileMessages("", null, "", 10).get().size());
    }

    @Test
    void searchFileMessagesFiltersByQueryOnFileName() throws Exception {
        FakeTelegramService telegram = new FakeTelegramService()
            .withMessages(message(1, 10, "report.pdf", "document"), message(2, 10, "cat.jpg", "photo"));

        List<TelegramFileMessage> result = telegram.searchFileMessages("REPORT", null, "", 10).get();

        assertEquals(1, result.size());
        assertEquals("report.pdf", result.get(0).fileName());
    }

    @Test
    void getFileMessageFindsByChatAndMessageId() throws Exception {
        FakeTelegramService telegram = new FakeTelegramService()
            .withMessages(message(7, 10, "report.pdf", "document"));

        TelegramFileMessage found = telegram.getFileMessage(10, 7).get();

        assertEquals("report.pdf", found.fileName());
    }

    @Test
    void getFileMessageFailsWhenNotFound() {
        FakeTelegramService telegram = new FakeTelegramService();

        ExecutionException thrown = assertThrows(ExecutionException.class,
            () -> telegram.getFileMessage(10, 7).get());

        assertEquals("Message not found", thrown.getCause().getMessage());
    }

    @Test
    void unauthorizedSessionFailsEveryOperationWithTheTypedException() {
        FakeTelegramService telegram = new FakeTelegramService().inState(SessionState.UNAUTHORIZED);

        assertUniformType(TelegramUnauthorizedException.class,
            telegram.getFileMessages(1, 0, 10),
            telegram.searchFileMessages("", null, "", 10),
            telegram.getFileMessage(1, 1),
            telegram.startDownload(1),
            telegram.getFileState(1));
    }

    @Test
    void uninitializedSessionFailsEveryOperationWithTheTypedException() {
        FakeTelegramService telegram = new FakeTelegramService().inState(SessionState.UNINITIALIZED);

        assertUniformType(TelegramNotInitializedException.class,
            telegram.getFileMessages(1, 0, 10),
            telegram.searchFileMessages("", null, "", 10),
            telegram.getFileMessage(1, 1),
            telegram.startDownload(1),
            telegram.getFileState(1));
    }

    @Test
    void unauthorizedAndUninitializedMessagesAreDistinguishable() throws Exception {
        FakeTelegramService unauthorized = new FakeTelegramService().inState(SessionState.UNAUTHORIZED);
        FakeTelegramService uninitialized = new FakeTelegramService().inState(SessionState.UNINITIALIZED);

        assertEquals("Telegram client not initialized", causeOf(uninitialized.getFileMessages(1, 0, 10)).getMessage());
        assertTrue(causeOf(unauthorized.getFileMessages(1, 0, 10)).getMessage().startsWith("Unauthorized."));
    }

    @Test
    void programmedFailurePropagatesThroughTheSeam() {
        RuntimeException boom = new RuntimeException("Telegram error 400: PEER_ID_INVALID");
        FakeTelegramService telegram = new FakeTelegramService().failingWith(boom);

        ExecutionException thrown = assertThrows(ExecutionException.class,
            () -> telegram.searchFileMessages("", null, "", 10).get());

        assertEquals(boom, thrown.getCause());
    }

    @Test
    void getOwnChatIdIsProgrammable() throws Exception {
        assertEquals(42L, new FakeTelegramService().withOwnChatId(42).getOwnChatId().get());
    }

    @Test
    void authWizardStateIsProgrammable() {
        FakeTelegramService telegram = new FakeTelegramService()
            .withAuthState(TelegramService.AUTH_STATE_WAIT_PHONE_NUMBER);

        assertEquals(TelegramService.AUTH_STATE_WAIT_PHONE_NUMBER, telegram.getAuthState());
        assertEquals(TelegramService.AUTH_STATE_READY, new FakeTelegramService().getAuthState());
    }

    @Test
    void authSubmissionsRecordTheirPayloads() throws Exception {
        FakeTelegramService telegram = new FakeTelegramService();

        telegram.submitPhoneNumber("+52 55 0000 0000").get();
        telegram.submitAuthCode("12345").get();
        telegram.submitAuthPassword("cloud-secret").get();

        assertEquals(List.of("phone:+52 55 0000 0000", "code:12345", "password:cloud-secret"),
            telegram.authSubmissions());
    }

    @Test
    void authSubmissionPropagatesTypedFailure() {
        FakeTelegramService telegram = new FakeTelegramService()
            .failingWith(new TelegramOperationException(400, "PHONE_NUMBER_INVALID"));

        ExecutionException thrown = assertThrows(ExecutionException.class,
            () -> telegram.submitPhoneNumber("123").get());

        assertInstanceOf(TelegramOperationException.class, thrown.getCause());
    }

    @Test
    void startDownloadReturnsInitialProgrammedState() throws Exception {
        FakeTelegramService telegram = new FakeTelegramService()
            .withFileState(new TelegramFileState(500, 2048, true, 512, "/tmp/partial.bin", false));

        TelegramFileState initial = telegram.startDownload(500).get();

        assertTrue(initial.downloadActive());
        assertEquals(512, initial.downloadedBytes());
        assertEquals(25, initial.progressPercent());
    }

    @Test
    void startDownloadOnUnknownFileDefaultsToActiveEmptyState() throws Exception {
        TelegramFileState initial = new FakeTelegramService().startDownload(999).get();

        assertTrue(initial.downloadActive());
        assertEquals(0, initial.downloadedBytes());
    }

    @Test
    void getFileStateReflectsProgrammedCompletion() throws Exception {
        FakeTelegramService telegram = new FakeTelegramService()
            .withFileState(new TelegramFileState(500, 2048, false, 2048, "/tmp/done.bin", true));

        TelegramFileState state = telegram.getFileState(500).get();

        assertTrue(state.downloaded());
        assertEquals(100, state.progressPercent());
    }

    @Test
    void getFileStateFailsForUnknownFile() {
        FakeTelegramService telegram = new FakeTelegramService();

        ExecutionException thrown = assertThrows(ExecutionException.class,
            () -> telegram.getFileState(404).get());

        assertTrue(thrown.getCause().getMessage().contains("File not found"));
    }

    @Test
    void sendDocumentReturnsProgrammedUploadResult() throws Exception {
        TelegramFileMessage uploaded = message(77, 10, "report.pdf", "document");
        FakeTelegramService telegram = new FakeTelegramService().withUploadResult(uploaded);

        TelegramFileMessage result = telegram.sendDocument(10, "/staging/report.pdf", null).get();

        assertEquals(77, result.messageId());
        assertEquals("/staging/report.pdf", telegram.trackedUploads().get((int) result.fileId()));
        assertTrue(telegram.isUploadTracked((int) result.fileId()));
    }

    @Test
    void sendPhotoDefaultsToSynthesizedUploadResult() throws Exception {
        FakeTelegramService telegram = new FakeTelegramService();

        TelegramFileMessage result = telegram.sendPhoto(10, "/staging/cat.jpg", "hello").get();

        assertEquals(10, result.chatId());
        assertEquals("cat.jpg", result.fileName());
        assertEquals("photo", result.type());
        assertTrue(telegram.isUploadTracked((int) result.fileId()));
    }

    @Test
    void deleteMessagesRecordsCallsForAssertion() throws Exception {
        FakeTelegramService telegram = new FakeTelegramService();

        telegram.deleteMessages(10, List.of(5L, 6L), true).get();

        assertEquals(List.of("10:5:true", "10:6:true"), telegram.deletedMessages());
    }

    @Test
    void deleteLocalFileRecordsCallsForAssertion() throws Exception {
        FakeTelegramService telegram = new FakeTelegramService();

        telegram.deleteLocalFile(500).get();

        assertEquals(List.of(500L), telegram.deletedLocalFiles());
    }

    // ==================== HELPERS ====================

    private static void assertUniformIllegalState(CompletableFuture<?>... futures) {
        for (CompletableFuture<?> future : futures) {
            ExecutionException thrown = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(IllegalStateException.class, thrown.getCause());
        }
    }

    private static void assertUniformType(Class<? extends Throwable> expected, CompletableFuture<?>... futures) {
        for (CompletableFuture<?> future : futures) {
            ExecutionException thrown = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(expected, thrown.getCause());
        }
    }

    private static Throwable causeOf(CompletableFuture<?> future) {
        CompletionException thrown = assertThrows(CompletionException.class, future::join);
        return thrown.getCause();
    }

    private static TelegramFileMessage photo(long messageId, long chatId) {
        return message(messageId, chatId, "photo_" + messageId + ".jpg", "photo");
    }

    private static TelegramFileMessage video(long messageId, long chatId) {
        return message(messageId, chatId, "video_" + messageId + ".mp4", "video");
    }

    private static TelegramFileMessage document(long messageId, long chatId) {
        return message(messageId, chatId, "doc_" + messageId + ".pdf", "document");
    }

    private static TelegramFileMessage message(long messageId, long chatId, String fileName, String type) {
        return new TelegramFileMessage(
            messageId, chatId, messageId * 100, fileName, 1024, null, type,
            null, null, null, null, 1700000000, false, null);
    }
}
