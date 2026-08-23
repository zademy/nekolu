/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.exception;

/**
 * The TDLib session exists but is not authenticated. The user must
 * authenticate through the TDLib CLI client before using the workspace.
 * Carries the standard message so diagnostics stay stable; the type is
 * what the HTTP layer maps to 401.
 */
public class TelegramUnauthorizedException extends IllegalStateException {

    public TelegramUnauthorizedException() {
        super("Unauthorized. Telegram requires authentication. Use the TDLib CLI client to authenticate first.");
    }
}
