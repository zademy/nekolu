/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.exception;

/**
 * The requested Telegram resource (message, file) does not exist or, when
 * a message was expected to carry a file, carries none. The HTTP layer
 * maps this to 404.
 */
public class TelegramNotFoundException extends RuntimeException {

    public TelegramNotFoundException(String message) {
        super(message);
    }
}
