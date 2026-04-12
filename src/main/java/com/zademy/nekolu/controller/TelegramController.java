/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.controller;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zademy.nekolu.dto.CreateFolderRequest;

import jakarta.validation.Valid;
import com.zademy.nekolu.dto.CreateFolderResponse;
import com.zademy.nekolu.dto.DeleteFolderResponse;
import com.zademy.nekolu.dto.FolderInfo;
import com.zademy.nekolu.service.TelegramService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Exposes folder-management endpoints backed by Telegram private channels.
 */
@RestController
@RequestMapping("/api/telegram")
@Tag(name = "Folders", description = "Folder lifecycle operations backed by Telegram private channels")
public class TelegramController {

    private final TelegramService telegramService;

    public TelegramController(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    @PostMapping("/folders")
    @Operation(
        summary = "Create folder",
        description = """
                Creates a logical folder by provisioning a private Telegram channel.
                The returned chat ID can later be used for folder-scoped file queries and uploads.
                """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Folder created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateFolderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed or the folder could not be created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateFolderResponse.class)))
    })
    public CompletableFuture<ResponseEntity<CreateFolderResponse>> createFolder(
            @RequestBody @Parameter(description = "Folder data to create", required = true)
            @Valid CreateFolderRequest request) {

        String title = request.title().trim();
        String description = request.description();

        return telegramService.createFolder(title, description != null ? description.trim() : null)
                .thenApply(chat -> ResponseEntity.ok(
                    new CreateFolderResponse(
                        chat.id,
                        chat.title,
                        description,
                        true,
                        "Folder created successfully"
                    )
                ))
                .exceptionally(ex -> {
                    String errorMsg = ex.getMessage();
                    if (ex.getCause() != null) {
                        errorMsg = ex.getCause().getMessage();
                    }
                    return ResponseEntity.badRequest().body(
                        new CreateFolderResponse(null, title, description, false, errorMsg)
                    );
                });
    }

    @GetMapping("/folders")
    @Operation(
        summary = "List folders",
        description = "Returns all folders currently backed by private Telegram channels for the authenticated TDLib session",
        tags = { "Folders" }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Folder list retrieved successfully",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = FolderInfo.class)))),
        @ApiResponse(responseCode = "400", description = "Folder list could not be retrieved")
    })
    public CompletableFuture<ResponseEntity<List<FolderInfo>>> listFolders() {
        return telegramService.listFolders()
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest().body(Collections.emptyList()));
    }

    @DeleteMapping("/folders/{chatId}")
    @Operation(
        summary = "Delete folder",
        description = """
                Deletes a folder by removing the backing private Telegram channel from the current session.
                This operation is irreversible from the application's perspective.
                """,
        tags = { "Folders" }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Folder deleted successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeleteFolderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Folder could not be deleted",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeleteFolderResponse.class)))
    })
    public CompletableFuture<ResponseEntity<DeleteFolderResponse>> deleteFolder(
            @PathVariable @Parameter(description = "Chat/channel ID to delete", example = "-1001234567890", required = true)
            long chatId) {

        return telegramService.deleteFolder(chatId)
                .thenApply(v -> ResponseEntity.ok(
                    new DeleteFolderResponse(chatId, true, "Folder deleted successfully")
                ))
                .exceptionally(ex -> {
                    String errorMsg = ex.getMessage();
                    if (ex.getCause() != null) {
                        errorMsg = ex.getCause().getMessage();
                    }
                    return ResponseEntity.badRequest().body(
                        new DeleteFolderResponse(chatId, false, errorMsg)
                    );
                });
    }
}
