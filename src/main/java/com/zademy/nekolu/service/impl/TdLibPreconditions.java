/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-12
 */

package com.zademy.nekolu.service.impl;

import java.util.concurrent.CompletableFuture;

import org.drinkless.tdlib.Client;

/**
 * Centralizes TDLib precondition checks that are repeated across service methods.
 * Provides helpers for both CompletableFuture-returning and direct-throw scenarios.
 */
final class TdLibPreconditions {

    private static final String CLIENT_NOT_INITIALIZED = "Telegram client not initialized";
    private static final String NOT_AUTHORIZED = "Unauthorized. Telegram requires authentication. "
            + "Use the TDLib CLI client to authenticate first.";

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
            return CompletableFuture.failedFuture(new IllegalStateException(CLIENT_NOT_INITIALIZED));
        }
        if (!isAuthorized) {
            return CompletableFuture.failedFuture(new IllegalStateException(NOT_AUTHORIZED));
        }
        return null;
    }

    /**
     * Throws immediately if the client is null or not authorized.
     * Useful for synchronous methods that do not return a CompletableFuture.
     */
    static void requireReadyOrThrow(Client client, boolean isAuthorized) {
        if (client == null) {
            throw new IllegalStateException(CLIENT_NOT_INITIALIZED);
        }
        if (!isAuthorized) {
            throw new IllegalStateException(NOT_AUTHORIZED);
        }
    }
}
