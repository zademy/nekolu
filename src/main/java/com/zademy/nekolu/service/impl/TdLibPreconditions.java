/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-12
 */

package com.zademy.nekolu.service.impl;

import java.util.concurrent.CompletableFuture;

import org.drinkless.tdlib.Client;

import com.zademy.nekolu.exception.TelegramNotInitializedException;
import com.zademy.nekolu.exception.TelegramUnauthorizedException;

/**
 * Centralizes TDLib precondition checks that are repeated across service methods.
 * Provides helpers for both CompletableFuture-returning and direct-throw scenarios.
 */
final class TdLibPreconditions {

    private TdLibPreconditions() {}

    /**
     * Returns a failed future if the client is null or not authorized.
     * Returns {@code null} when preconditions pass, so callers can do:
     * <pre>
     * var failed = TdLibPreconditions.requireReady(client, authorized);
     * if (failed != null) return failed;
     * </pre>
     */
    static <T> CompletableFuture<T> requireReady(Client client, boolean isAuthorized) {
        if (client == null) {
            return CompletableFuture.failedFuture(new TelegramNotInitializedException());
        }
        if (!isAuthorized) {
            return CompletableFuture.failedFuture(new TelegramUnauthorizedException());
        }
        return null;
    }

    /**
     * Throws immediately if the client is null or not authorized.
     * Useful for synchronous methods that do not return a CompletableFuture.
     */
    static void requireReadyOrThrow(Client client, boolean isAuthorized) {
        if (client == null) {
            throw new TelegramNotInitializedException();
        }
        if (!isAuthorized) {
            throw new TelegramUnauthorizedException();
        }
    }
}
