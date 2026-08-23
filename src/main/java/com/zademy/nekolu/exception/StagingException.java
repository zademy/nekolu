/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.exception;

/**
 * Server-side staging failure (disk I/O while materializing an upload).
 * Not the client's fault: surfaces as 500 through the global handler.
 */
public class StagingException extends RuntimeException {

    public StagingException(String message, Throwable cause) {
        super(message, cause);
    }
}
