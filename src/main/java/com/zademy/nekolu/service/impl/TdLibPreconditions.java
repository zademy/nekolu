/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-12
 */

package com.zademy.nekolu.service.impl;

import java.util.Optional;
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
     * Readiness failure for futures: present when the client is missing or
     * unauthorized, {@link Optional#empty()} when preconditions pass:
     * <pre>
     * var failure = TdLibPreconditions.readinessFailure(client, authorized);
     * if (failure.isPresent()) return failure.get();
     * </pre>
     */
    static <T> Optional<CompletableFuture<T>> readinessFailure(Client client, boolean isAuthorized) {
        if (client == null) {
            return Optional.of(CompletableFuture.failedFuture(new TelegramNotInitializedException()));
        }
        if (!isAuthorized) {
            return Optional.of(CompletableFuture.failedFuture(new TelegramUnauthorizedException()));
        }
        return Optional.empty();
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
