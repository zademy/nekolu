/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.constants;

/**
 * Holds shared default values and timeout settings used by service-layer workflows.
 */
public final class ServiceDefaults {
    public static final int MIN_RESULTS_PER_TYPE = 20;
    public static final int DEFAULT_RECENT_FILES_LIMIT = 50;
    public static final int DEFAULT_STATS_FILE_LIMIT = 1000;
    public static final int DEFAULT_EXPORT_FILE_LIMIT = 10000;
    public static final int DEFAULT_CHAT_LIST_LIMIT = 200;
    public static final long STREAM_DOWNLOAD_TIMEOUT_MS = 60000L;
    public static final long SSE_TIMEOUT_MS = 300000L;
    public static final int TDLIB_REQUEST_TIMEOUT_SECONDS = 30;

    private ServiceDefaults() {}
}
