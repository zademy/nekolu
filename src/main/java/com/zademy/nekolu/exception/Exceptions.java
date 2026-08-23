/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.exception;

import java.util.concurrent.CompletionException;

/**
 * Single unwrapping point for futures-completed exceptions: async stages
 * wrap failures in CompletionException, and every consumer needs the cause.
 */
public final class Exceptions {

    private Exceptions() {}

    /**
     * Returns the cause of a CompletionException, or the error itself when
     * it is not wrapped.
     */
    public static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
