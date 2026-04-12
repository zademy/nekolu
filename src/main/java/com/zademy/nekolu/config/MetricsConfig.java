/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-12
 */

package com.zademy.nekolu.config;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Registers custom Micrometer metrics for TDLib operations.
 */
@Component
public class MetricsConfig {

    private final Counter downloadsStarted;
    private final Counter uploadsStarted;
    private final Counter foldersCreated;
    private final Counter tdlibErrors;
    private final AtomicInteger activeDownloads = new AtomicInteger(0);

    public MetricsConfig(MeterRegistry registry) {
        this.downloadsStarted = Counter.builder("nekolu.downloads.started")
                .description("Total file downloads started")
                .register(registry);

        this.uploadsStarted = Counter.builder("nekolu.uploads.started")
                .description("Total file uploads started")
                .register(registry);

        this.foldersCreated = Counter.builder("nekolu.folders.created")
                .description("Total folders created")
                .register(registry);

        this.tdlibErrors = Counter.builder("nekolu.tdlib.errors")
                .description("Total TDLib operation errors")
                .register(registry);

        Gauge.builder("nekolu.downloads.active", activeDownloads, AtomicInteger::get)
                .description("Currently active downloads")
                .register(registry);
    }

    public void incrementDownloadsStarted() {
        downloadsStarted.increment();
        activeDownloads.incrementAndGet();
    }

    public void decrementActiveDownloads() {
        activeDownloads.decrementAndGet();
    }

    public void incrementUploadsStarted() {
        uploadsStarted.increment();
    }

    public void incrementFoldersCreated() {
        foldersCreated.increment();
    }

    public void incrementTdlibErrors() {
        tdlibErrors.increment();
    }
}
