/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

import com.zademy.nekolu.dto.FileInfoResponse;

class MetadataIndexServiceImplTest {
    private final MetadataIndexServiceImpl metadataIndexService = new MetadataIndexServiceImpl();

    @Test
    void shouldEnrichFileWithSafeLogicalDefaultsWhenMetadataIsMissing() {
        FileInfoResponse base = new FileInfoResponse(
            10L,
            20L,
            30L,
            "report.pdf",
            1024L,
            "application/pdf",
            "DOCUMENT",
            null,
            null,
            null,
            null,
            1775260000L,
            false,
            null
        );

        FileInfoResponse enriched = metadataIndexService.enrich(base).join();

        assertThat(enriched.logicalFileId()).isEqualTo("telegram-30");
        assertThat(enriched.virtualPath()).isEqualTo("/");
        assertThat(enriched.tags()).isEmpty();
        assertThat(enriched.trashed()).isFalse();
        assertThat(enriched.downloadUrl()).isNull();
        assertThat(enriched.streamUrl()).isEqualTo("/api/telegram/files/30/stream");
    }

    @Test
    void shouldReturnSyntheticMetadataWithoutPersistingMessages() {
        FileInfoResponse uploaded = new FileInfoResponse(
            80L,
            20L,
            31L,
            "report.pdf",
            2048L,
            "application/pdf",
            "DOCUMENT",
            null,
            null,
            null,
            null,
            1775260000L,
            false,
            null
        );

        var saved = metadataIndexService.registerUploadedFile(
            uploaded,
            "def456",
            "/projects",
            List.of("work", "backup"),
            "telegram-upload",
            false
        ).join();

        assertThat(saved.fileId()).isEqualTo(31L);
        assertThat(saved.version()).isEqualTo(1);
        assertThat(saved.virtualPath()).isEqualTo("/projects");
        assertThat(saved.tags()).containsExactly("work", "backup");
        assertThat(saved.metadataMessageId()).isZero();
    }

    @Test
    void shouldDisableLogicalTrashOperations() {
        assertThat(metadataIndexService.moveToTrash(30L).join()).isEmpty();
        assertThat(metadataIndexService.restore(30L).join()).isEmpty();
        assertThat(metadataIndexService.listTrash().join()).isEmpty();
    }
}
