/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.zademy.nekolu.dto.FileInfoResponse;
import com.zademy.nekolu.model.TelegramFileMessage;
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
