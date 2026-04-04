/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Startup maintenance component that removes stale staged upload files left behind by interrupted uploads.
 */
@Component
public class TemporaryUploadJanitor {
    private static final Logger logger = LoggerFactory.getLogger(TemporaryUploadJanitor.class);
    private static final Duration MAX_FILE_AGE = Duration.ofHours(12);
    private static final Path STAGING_DIRECTORY = Path.of("tdlib", "upload-staging");

    @PostConstruct
    public void cleanup() {
        Instant threshold = Instant.now().minus(MAX_FILE_AGE);

        if (!Files.isDirectory(STAGING_DIRECTORY)) {
            return;
        }

        try (Stream<Path> files = Files.list(STAGING_DIRECTORY)) {
            files.filter(Files::isRegularFile)
                .forEach(path -> deleteIfExpired(path, threshold));
        } catch (IOException e) {
            logger.warn("Could not inspect staged upload files: {}", e.getMessage());
        }
    }

    private void deleteIfExpired(Path path, Instant threshold) {
        try {
            FileTime lastModifiedTime = Files.getLastModifiedTime(path);
            if (lastModifiedTime.toInstant().isBefore(threshold)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            logger.warn("Could not remove stale staged upload file {}: {}", path, e.getMessage());
        }
    }
}
