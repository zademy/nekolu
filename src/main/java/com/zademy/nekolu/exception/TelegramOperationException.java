/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.exception;

/**
 * Telegram (upstream) rejected or failed an operation, reported by TDLib
 * with an error code and message. The HTTP layer maps this to 502: the
 * failure is upstream, not in the caller's request.
 */
public class TelegramOperationException extends RuntimeException {

    private final int code;

    public TelegramOperationException(int code, String message) {
        super("Telegram error " + code + ": " + message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
