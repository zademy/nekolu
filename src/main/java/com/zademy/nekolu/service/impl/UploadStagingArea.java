/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.service.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.zademy.nekolu.exception.StagingException;

import jakarta.annotation.PostConstruct;

/**
 * Deep module owning the staged-upload cycle: materializing an incoming
 * upload into the staging directory (the single place the path exists),
 * discarding staged files whose publication failed, and purging leftovers
 * from interrupted uploads on startup. Publishing through TDLib and the
 * post-confirmation cleanup live behind the Telegram seam.
 */
@Component
public class UploadStagingArea {

    private static final Logger logger = LoggerFactory.getLogger(UploadStagingArea.class);
    private static final Duration LEFTOVER_RETENTION = Duration.ofHours(12);
    private static final String FALLBACK_NAME = "upload.bin";

    private final Path directory;

    /**
     * The staging directory is configurable; the default keeps the
     * historical location. Tests inject an explicit directory through the
     * same constructor.
     */
    public UploadStagingArea(@Value("${nekolu.upload-staging-directory:tdlib/upload-staging}") Path directory) {
        this.directory = directory;
    }

    /**
     * Purges staged files left over by interrupted uploads, so the disk does
     * not grow without bound across restarts.
     */
    @PostConstruct
    public void purgeExpired() {
        File[] uploadDirs = directory.toFile().listFiles();
        if (uploadDirs == null) {
            return;
        }
        long cutoff = Instant.now().minus(LEFTOVER_RETENTION).toEpochMilli();
        for (File uploadDir : uploadDirs) {
            if (uploadDir.isDirectory() && uploadDir.lastModified() < cutoff) {
                try {
                    Files.walk(uploadDir.toPath())
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
                    logger.info("[Upload] Purged expired upload directory: {}", uploadDir.getAbsolutePath());
                } catch (IOException e) {
                    logger.warn("[Upload] Could not purge expired upload directory: {}", uploadDir.getAbsolutePath());
                }
            } else if (uploadDir.isFile() && uploadDir.lastModified() < cutoff && uploadDir.delete()) {
                // Legacy flat files from older versions
                logger.info("[Upload] Purged expired staged file: {}", uploadDir.getAbsolutePath());
            }
        }
    }

    /**
     * Materializes an incoming upload as a staged file. Each upload gets its
     * own UUID subdirectory — the file inside carries the original (sanitized)
     * name, so Telegram shows the user's filename, not a prefixed one, while
     * the subdirectory prevents collisions between same-named uploads.
     */
    public File stage(String originalFilename, InputStream content) {
        try {
            Path uploadDir = directory.resolve(UUID.randomUUID().toString());
            Files.createDirectories(uploadDir);
            Path staged = uploadDir.resolve(sanitize(originalFilename));
            Files.copy(content, staged, StandardCopyOption.REPLACE_EXISTING);
            return staged.toFile();
        } catch (IOException e) {
            // Server-side disk failure: must not surface as a client error.
            throw new StagingException("Could not stage upload file: " + originalFilename, e);
        }
    }

    /**
     * Best-effort removal of a staged upload (the UUID subdirectory) whose
     * publication failed.
     */
    public void discard(File staged) {
        if (staged == null || !staged.exists()) {
            return;
        }
        File uploadDir = staged.getParentFile();
        if (uploadDir != null && uploadDir.getParentFile() != null
                && uploadDir.getParentFile().equals(directory.toFile())) {
            // Remove the whole UUID subdirectory
            try {
                Files.walk(uploadDir.toPath())
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> path.toFile().delete());
                logger.info("[Upload] Discarded staged upload directory: {}", uploadDir.getAbsolutePath());
            } catch (IOException e) {
                logger.warn("[Upload] Could not discard staged upload: {}", uploadDir.getAbsolutePath());
            }
        } else if (staged.delete()) {
            logger.info("[Upload] Discarded staged file after failed upload: {}", staged.getAbsolutePath());
        }
    }

    private String sanitize(String originalFilename) {
        String sanitized = originalFilename != null
            ? originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_")
            : FALLBACK_NAME;
        return sanitized.isBlank() ? FALLBACK_NAME : sanitized;
    }
}
