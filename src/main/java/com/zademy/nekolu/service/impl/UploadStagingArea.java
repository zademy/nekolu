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
import org.springframework.stereotype.Component;

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

    static final Path DEFAULT_DIRECTORY = Path.of("tdlib", "upload-staging");

    private final Path directory;

    public UploadStagingArea() {
        this(DEFAULT_DIRECTORY);
    }

    /**
     * Testing seam: staging against an explicit directory.
     */
    public UploadStagingArea(Path directory) {
        this.directory = directory;
    }

    /**
     * Purges staged files left over by interrupted uploads, so the disk does
     * not grow without bound across restarts.
     */
    @PostConstruct
    public void purgeExpired() {
        File[] leftovers = directory.toFile().listFiles();
        if (leftovers == null) {
            return;
        }
        long cutoff = Instant.now().minus(LEFTOVER_RETENTION).toEpochMilli();
        for (File leftover : leftovers) {
            if (leftover.isFile() && leftover.lastModified() < cutoff && leftover.delete()) {
                logger.info("[Upload] Purged expired staged file: {}", leftover.getAbsolutePath());
            }
        }
    }

    /**
     * Materializes an incoming upload as a staged file with a unique,
     * sanitized name in the staging directory.
     */
    public File stage(String originalFilename, InputStream content) {
        try {
            Files.createDirectories(directory);
            Path staged = directory.resolve(UUID.randomUUID() + "_" + sanitize(originalFilename));
            Files.copy(content, staged, StandardCopyOption.REPLACE_EXISTING);
            return staged.toFile();
        } catch (IOException e) {
            throw new IllegalStateException("Could not stage upload file: " + originalFilename, e);
        }
    }

    /**
     * Best-effort removal of a staged file whose publication failed.
     */
    public void discard(File staged) {
        if (staged != null && staged.exists() && staged.delete()) {
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
