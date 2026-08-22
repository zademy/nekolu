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

import com.zademy.nekolu.model.TelegramFileMessage;
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
    void unauthorizedSessionFailsEveryOperationWithTheStandardMessage() {
        FakeTelegramService telegram = new FakeTelegramService().inState(SessionState.UNAUTHORIZED);

        assertUniformIllegalState(
            telegram.getFileMessages(1, 0, 10),
            telegram.searchFileMessages("", null, "", 10),
            telegram.getFileMessage(1, 1));
    }

    @Test
    void uninitializedSessionFailsEveryOperationWithTheStandardMessage() {
        FakeTelegramService telegram = new FakeTelegramService().inState(SessionState.UNINITIALIZED);

        assertUniformIllegalState(
            telegram.getFileMessages(1, 0, 10),
            telegram.searchFileMessages("", null, "", 10),
            telegram.getFileMessage(1, 1));
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

    // ==================== HELPERS ====================

    private static void assertUniformIllegalState(CompletableFuture<?>... futures) {
        for (CompletableFuture<?> future : futures) {
            ExecutionException thrown = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(IllegalStateException.class, thrown.getCause());
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
