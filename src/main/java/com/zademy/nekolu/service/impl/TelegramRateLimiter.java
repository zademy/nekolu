/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-12
 */

package com.zademy.nekolu.service.impl;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Token-bucket-style rate limiter that protects the TDLib client from
 * exceeding Telegram's flood-wait thresholds.
 * <p>
 * Callers invoke {@link #acquire()} before sending a TDLib request;
 * if the bucket is empty the call blocks until a permit is available.
 */
@Component
public class TelegramRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(TelegramRateLimiter.class);

    private final Semaphore semaphore;
    private final long acquireTimeoutMs;

    public TelegramRateLimiter(
            @Value("${nekolu.rate-limit.max-concurrent:20}") int maxConcurrent,
            @Value("${nekolu.rate-limit.acquire-timeout-ms:5000}") long acquireTimeoutMs) {
        this.semaphore = new Semaphore(maxConcurrent);
        this.acquireTimeoutMs = acquireTimeoutMs;
    }

    /**
     * Acquires a permit to perform a TDLib operation, blocking if necessary.
     *
     * @throws IllegalStateException if the timeout expires before a permit is available
     */
    public void acquire() {
        try {
            if (!semaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS)) {
                logger.warn("Rate limiter timeout — {} requests waiting", semaphore.getQueueLength());
                throw new IllegalStateException(
                        "Too many concurrent Telegram requests. Please try again later.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Request interrupted while waiting for rate limiter", e);
        }
    }

    /**
     * Releases a previously acquired permit.
     */
    public void release() {
        semaphore.release();
    }

    /**
     * Returns the number of permits currently available.
     */
    public int availablePermits() {
        return semaphore.availablePermits();
    }
}
