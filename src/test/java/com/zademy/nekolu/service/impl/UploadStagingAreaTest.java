/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.zademy.nekolu.exception.StagingException;

/**
 * Unit tests for the upload staging area against a real temporary
 * directory: materialization, sanitization, discard, and leftover purging
 * — the whole staged-file cycle without Telegram.
 */
class UploadStagingAreaTest {

    @TempDir
    File tempDir;

    private UploadStagingArea stagingArea;

    @BeforeEach
    void setUp() {
        stagingArea = new UploadStagingArea(tempDir.toPath());
    }

    @Test
    void stageMaterializesContentWithASanitizedName() throws Exception {
        File staged = stagingArea.stage("../evil name?.pdf", new ByteArrayInputStream("payload".getBytes()));

        assertTrue(staged.exists());
        assertEquals("payload".length(), staged.length());
        // Unsafe characters neutralized, unique prefix kept, and the staged
        // file stays inside the staging directory (no traversal escape)
        assertTrue(staged.getName().endsWith(".._evil_name_.pdf"));
        assertEquals(tempDir.toPath(), staged.toPath().getParent());
    }

    @Test
    void stageFallsBackToAGenericNameWhenMissing() {
        File staged = stagingArea.stage(null, new ByteArrayInputStream(new byte[0]));

        assertTrue(staged.exists());
        assertTrue(staged.getName().endsWith("upload.bin"));
    }

    @Test
    void discardRemovesTheStagedFile() {
        File staged = stagingArea.stage("gone.pdf", new ByteArrayInputStream("x".getBytes()));

        stagingArea.discard(staged);

        assertFalse(staged.exists());
    }

    @Test
    void purgeExpiredRemovesOnlyOldLeftovers() throws Exception {
        Path fresh = Files.writeString(tempDir.toPath().resolve("fresh.dat"), "fresh");
        Path old = Files.writeString(tempDir.toPath().resolve("old.dat"), "old");
        // 13 hours old, past the 12-hour retention
        assertTrue(old.toFile().setLastModified(System.currentTimeMillis() - 13 * 3600_000L));

        stagingArea.purgeExpired();

        assertTrue(fresh.toFile().exists());
        assertFalse(old.toFile().exists());
    }

    @Test
    void stageSurfacesDiskFailuresAsStagingException() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk on fire");
            }
        };

        StagingException thrown = assertThrows(StagingException.class,
            () -> stagingArea.stage("any.bin", broken));

        assertTrue(thrown.getMessage().contains("any.bin"));
        assertEquals("disk on fire", thrown.getCause().getMessage());
    }
}
