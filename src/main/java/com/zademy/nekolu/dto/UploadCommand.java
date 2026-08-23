/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.dto;

import java.io.InputStream;
import java.util.List;

/**
 * The single upload command: everything an upload needs, travelling as one
 * value. Adding a parameter means adding a component here — never a new
 * method signature.
 *
 * @param originalFilename the client-provided file name
 * @param content the upload content
 * @param chatId the target chat (Saved Messages or a folder channel)
 * @param caption the optional caption
 * @param virtualPath the logical virtual path (descriptive)
 * @param tags the logical tags (descriptive)
 * @param origin the logical origin (descriptive)
 * @param archived whether the file starts archived (descriptive)
 * @param photoMode true publishes as a photo, false as a document
 */
public record UploadCommand(
        String originalFilename,
        InputStream content,
        long chatId,
        String caption,
        String virtualPath,
        List<String> tags,
        String origin,
        boolean archived,
        boolean photoMode) {
}
