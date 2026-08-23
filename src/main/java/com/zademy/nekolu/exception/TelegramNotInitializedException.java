/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.exception;

/**
 * The Telegram module has no TDLib client yet (startup not finished).
 * Callers should retry later; the HTTP layer maps this to 503.
 */
public class TelegramNotInitializedException extends IllegalStateException {

    public TelegramNotInitializedException() {
        super("Telegram client not initialized");
    }
}
