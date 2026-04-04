/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

/**
 * Configures the generated OpenAPI document exposed through SpringDoc.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Nekolu API")
                        .version("1.0.0")
                        .description("""
                                Nekolu exposes a Telegram-backed personal file workspace built on TDLib.

                                The API covers:
                                - file discovery with filters, sorting, and folder scoping
                                - uploads, downloads, inline previews, thumbnails, and streaming metadata
                                - logical drive operations such as move, archive, restore, and trash workflows
                                - batch downloads, progress monitoring, statistics, and structured exports
                                - Telegram folder management backed by private channels

                                Operational notes:
                                - TDLib must already be authenticated before the API can be used successfully
                                - only one process can use the same TDLib database directory at a time
                                - a file is considered downloaded only when TDLib reports a completed local copy
                                - local upload staging files are implementation details and must not be treated as downloaded files
                                """))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development server")
                ))
                .tags(List.of(
                        new Tag().name("Files").description("File discovery, transfer, preview, logical metadata, statistics, and bulk operations"),
                        new Tag().name("Folders").description("Folder lifecycle operations and folder-scoped file listing")
                ));
    }
}
