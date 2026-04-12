/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-12
 */

package com.zademy.nekolu.config;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

import com.zademy.nekolu.service.TelegramService;

/**
 * Custom health indicator that reports TDLib authorization status,
 * client availability, and local file-system accessibility.
 */
@Component
public class TelegramHealthIndicator extends AbstractHealthIndicator {

    private final TelegramService telegramService;
    private final TelegramConfig telegramConfig;

    public TelegramHealthIndicator(TelegramService telegramService, TelegramConfig telegramConfig) {
        super("Telegram health check failed");
        this.telegramService = telegramService;
        this.telegramConfig = telegramConfig;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        boolean clientReady = telegramService.getClient() != null;
        boolean authorized = telegramService.isAuthorized();

        builder.withDetail("clientInitialized", clientReady);
        builder.withDetail("authorized", authorized);

        Path dbDir = Path.of(telegramConfig.getDatabaseDirectory());
        boolean dbAccessible = Files.isDirectory(dbDir) && Files.isWritable(dbDir);
        builder.withDetail("databaseDirectory", dbDir.toString());
        builder.withDetail("databaseAccessible", dbAccessible);

        Path filesDir = Path.of(telegramConfig.getFilesDirectory());
        boolean filesAccessible = Files.isDirectory(filesDir) && Files.isWritable(filesDir);
        builder.withDetail("filesDirectory", filesDir.toString());
        builder.withDetail("filesAccessible", filesAccessible);

        try {
            long usableSpace = filesDir.toFile().getUsableSpace();
            builder.withDetail("usableDiskSpaceBytes", usableSpace);
            if (usableSpace < 104_857_600L) { // < 100 MB
                builder.withDetail("diskSpaceWarning", "Less than 100 MB available");
            }
        } catch (SecurityException e) {
            builder.withDetail("usableDiskSpaceBytes", "unavailable");
        }

        if (clientReady && authorized && dbAccessible && filesAccessible) {
            builder.up();
        } else if (clientReady && !authorized) {
            builder.down().withDetail("reason", "TDLib session not authorized");
        } else {
            builder.down();
        }
    }
}
