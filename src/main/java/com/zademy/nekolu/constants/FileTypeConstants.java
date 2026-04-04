/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.constants;

import java.util.List;

/**
 * Centralizes logical file-type identifiers used by the API, filters, and user interface.
 */
public final class FileTypeConstants {
    public static final String ALL = "all";
    public static final String PHOTO = "photo";
    public static final String VIDEO = "video";
    public static final String AUDIO = "audio";
    public static final String DOCUMENT = "document";
    public static final String VOICE = "voice";
    public static final String VIDEO_NOTE = "video_note";
    public static final String ANIMATION = "animation";
    public static final String STICKER = "sticker";
    public static final String THUMBNAIL = "thumbnail";
    public static final String PROFILE_PHOTO = "profile_photo";
    public static final String WALLPAPER = "wallpaper";
    public static final String OTHER = "other";
    public static final String UNKNOWN = "unknown";

    public static final String PHOTO_KIND = "PHOTO";
    public static final String VIDEO_KIND = "VIDEO";
    public static final String AUDIO_KIND = "AUDIO";
    public static final String DOCUMENT_KIND = "DOCUMENT";
    public static final String VOICE_KIND = "VOICE";
    public static final String VIDEO_NOTE_KIND = "VIDEO_NOTE";
    public static final String FILE_KIND = "FILE";

    public static final List<String> SEARCHABLE_TYPES = List.of(
        PHOTO, VIDEO, AUDIO, DOCUMENT, VOICE, VIDEO_NOTE
    );

    public static final List<String> COMBINED_SEARCH_TYPES = List.of(
        PHOTO, VIDEO, AUDIO, DOCUMENT
    );

    private FileTypeConstants() {}
}
