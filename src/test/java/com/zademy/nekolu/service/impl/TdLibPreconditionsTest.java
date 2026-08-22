/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the centralized TDLib readiness preconditions used by the
 * request guard. Only the uninitialized-client paths run without a live TDLib
 * session; a real Client instance cannot be created in tests, so the
 * authorized paths are covered by the seam contract tests instead.
 */
class TdLibPreconditionsTest {

    @Test
    void requireReadyFailsWhenClientIsNotInitialized() throws Exception {
        CompletableFuture<Object> failed = TdLibPreconditions.requireReady(null, true);

        assertNotNull(failed);
        ExecutionException thrown = assertThrowsExecution(failed);
        assertTrue(thrown.getCause() instanceof IllegalStateException);
        assertEquals("Telegram client not initialized", thrown.getCause().getMessage());
    }

    @Test
    void requireReadyChecksClientBeforeAuthorization() throws Exception {
        // A null client wins over the authorization flag: the message must be
        // the initialization one even when the session is also unauthorized.
        CompletableFuture<Object> failed = TdLibPreconditions.requireReady(null, false);

        ExecutionException thrown = assertThrowsExecution(failed);
        assertEquals("Telegram client not initialized", thrown.getCause().getMessage());
    }

    @Test
    void requireReadyOrThrowThrowsWhenClientIsNotInitialized() {
        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> TdLibPreconditions.requireReadyOrThrow(null, true));

        assertEquals("Telegram client not initialized", thrown.getMessage());
    }

    private static ExecutionException assertThrowsExecution(CompletableFuture<?> failed) {
        return assertThrows(
            ExecutionException.class,
            () -> failed.get(1, TimeUnit.SECONDS));
    }
}
