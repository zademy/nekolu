/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.drinkless.tdlib.TdApi;
import org.junit.jupiter.api.Test;

import com.zademy.nekolu.exception.TelegramNotInitializedException;

/**
 * Unit tests for the centralized TDLib readiness preconditions used by the
 * request guard. Only the uninitialized-client paths run without a live
 * TDLib session; a real Client instance cannot be created in tests, so the
 * authorized paths are covered by the seam contract tests instead.
 */
class TdLibPreconditionsTest {

    @Test
    void readinessFailureIsPresentWhenClientIsNotInitialized() throws Exception {
        Optional<CompletableFuture<TdApi.Object>> failure =
            TdLibPreconditions.readinessFailure(null, true);

        assertTrue(failure.isPresent());
        ExecutionException thrown = assertThrowsExecution(failure.get());
        assertTrue(thrown.getCause() instanceof TelegramNotInitializedException);
        assertEquals("Telegram client not initialized", thrown.getCause().getMessage());
    }

    @Test
    void readinessFailureChecksClientBeforeAuthorization() throws Exception {
        // A null client wins over the authorization flag: the failure must be
        // the initialization one even when the session is also unauthorized.
        Optional<CompletableFuture<TdApi.Object>> failure =
            TdLibPreconditions.readinessFailure(null, false);

        assertTrue(failure.isPresent());
        assertEquals("Telegram client not initialized",
            assertThrowsExecution(failure.get()).getCause().getMessage());
    }

    @Test
    void requireReadyOrThrowThrowsWhenClientIsNotInitialized() {
        TelegramNotInitializedException thrown = org.junit.jupiter.api.Assertions.assertThrows(
            TelegramNotInitializedException.class,
            () -> TdLibPreconditions.requireReadyOrThrow(null, true));

        assertEquals("Telegram client not initialized", thrown.getMessage());
    }

    private static ExecutionException assertThrowsExecution(CompletableFuture<?> failed) {
        return org.junit.jupiter.api.Assertions.assertThrows(
            ExecutionException.class,
            () -> failed.get());
    }
}
