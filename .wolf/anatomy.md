# anatomy.md

> Auto-maintained by OpenWolf. Last scanned: 2026-08-23T05:54:56.831Z
> Files: 153 tracked | Anatomy hits: 0 | Misses: 0

> Project structure index. Auto-maintained by OpenWolf hooks and daemon.
> Run `openwolf scan` to generate, or wait for the first Claude Code session.
> Status: Pending initial scan

## ./

- `.classpath` (~614 tok)
- `.factorypath` (~46 tok)
- `.gitattributes` — Git attributes (~11 tok)
- `.gitignore` — Git ignore rules (~250 tok)
- `.project` (~222 tok)
- `AGENTS.md` — AGENTS.md (~7852 tok)
- `CLAUDE.md` — OpenWolf (~99 tok)
- `CODE_OF_CONDUCT.md` — Code of Conduct (~641 tok)
- `CONTEXT.md` — CONTEXT.md — Glosario del workspace (~662 tok)
- `CONTRIBUTING.adoc` (~899 tok)
- `docker-compose.yml` — Docker Compose services (~285 tok)
- `Dockerfile` — Docker container definition (~219 tok)
- `GEMINI.md` — OpenWolf (~75 tok)
- `HELP.md` — Getting Started (~387 tok)
- `LICENSE` — Project license (~288 tok)
- `mvnw` — or more contributor license agreements.  See the NOTICE file (~3144 tok)
- `mvnw.cmd` — Declares Directory (~2212 tok)
- `pom.xml` (~1531 tok)
- `README.md` — Project documentation (~3854 tok)
- `SECURITY.md` — Security Policy (~742 tok)
- `tdlib.log` (~0 tok)

## .github/workflows/

- `generate-changelog.yml` — CI: Generate Changelog (~773 tok)

## .mvn/wrapper/

- `maven-wrapper.properties` (~45 tok)

## .playwright-mcp/

- `console-2026-08-22T23-38-08-399Z.log` (~299 tok)
- `console-2026-08-22T23-50-13-302Z.log` (~35 tok)
- `page-2026-08-22T23-38-09-061Z.yml` (~202 tok)
- `page-2026-08-22T23-50-13-675Z.yml` — Declares for (~1246 tok)
- `page-2026-08-22T23-50-28-755Z.yml` — Declares for (~2614 tok)
- `page-2026-08-22T23-50-59-646Z.yml` — Declares for (~1540 tok)
- `page-2026-08-22T23-51-23-241Z.yml` (~0 tok)
- `page-2026-08-22T23-51-31-885Z.yml` — Declares for (~1538 tok)
- `page-2026-08-22T23-51-46-148Z.yml` — Declares for (~11966 tok)
- `page-2026-08-22T23-52-04-660Z.yml` — Declares for (~12569 tok)
- `page-2026-08-22T23-52-14-584Z.yml` — Declares for (~12022 tok)
- `page-2026-08-22T23-52-28-052Z.yml` — Declares for (~987 tok)
- `page-2026-08-22T23-52-45-887Z.yml` — Declares for (~1802 tok)
- `page-2026-08-22T23-53-09-414Z.yml` — Declares for (~1779 tok)
- `page-2026-08-22T23-53-20-974Z.yml` — Declares for (~1779 tok)
- `page-2026-08-22T23-55-55-915Z.yml` — Declares for (~1801 tok)
- `page-2026-08-22T23-56-13-826Z.yml` — Declares for (~1540 tok)
- `page-2026-08-22T23-56-37-844Z.yml` — Declares for (~12483 tok)
- `page-2026-08-22T23-57-03-734Z.yml` — Declares for (~12483 tok)
- `page-2026-08-22T23-57-08-753Z.yml` — Declares for (~1538 tok)
- `page-2026-08-22T23-57-22-109Z.yml` — Declares for (~12000 tok)

## .qoder/rules/

- `agents_reference.md` — Referencia a AGENTS.md (~810 tok)
- `anti_patterns.md` — Antipatrones Spring Boot 4 y Java 25 (~7473 tok)
- `api_rest_conventions.md` — Convenciones de API REST (~1371 tok)
- `architecture.md` — Arquitectura del Proyecto (~766 tok)
- `design_patterns.md` — Patrones de Diseño Spring Boot 4 y Java 25 (~7876 tok)
- `java_coding_standards.md` — Estándares de Código Java 25 (~1271 tok)
- `java_evolution_guide.md` — Guía de Evolución Java - Features Modernas 8-25 (~3574 tok)
- `java_rules_compendium.md` — Compendio de Reglas Java - Integración Completa (~2319 tok)
- `java_style_guide_formatting.md` — Java Style Guide - Formato (~4932 tok)
- `java_style_guide_javadoc.md` — Java Style Guide - Javadoc (~2875 tok)
- `java_style_guide_naming.md` — Java Style Guide - Nombramiento (~3846 tok)
- `java_style_guide_programming_practices.md` — Java Style Guide - Prácticas de Programación (~3627 tok)
- `java_style_guide_source_file_basics.md` — Java Style Guide - Básicos de Archivo Fuente (~1161 tok)
- `java_style_guide_source_file_structure.md` — Java Style Guide - Estructura de Archivo Fuente (~1959 tok)
- `java_style_guide_specific_constructs.md` — Java Style Guide - Constructos Específicos (~3523 tok)
- `mcp_usage.md` — Uso de Herramientas MCP (~728 tok)
- `tdlib_integration.md` — Integración TDLib Native (~1373 tok)

## .settings/

- `org.eclipse.core.resources.prefs` (~40 tok)
- `org.eclipse.jdt.apt.core.prefs` (~58 tok)
- `org.eclipse.jdt.core.prefs` (~347 tok)
- `org.eclipse.m2e.core.prefs` (~23 tok)

## docs/adr/

- `0001-recortar-drive-logico.md` — ADR-0001: Recortar el drive lógico en lugar de materializarlo (~539 tok)

## docs/agents/

- `domain.md` — Domain Docs (~484 tok)
- `issue-tracker.md` — Issue tracker: GitHub (~933 tok)
- `triage-labels.md` — Triage Labels (~262 tok)

## src/main/java/com/zademy/nekolu/

- `Application.java` — Bootstrap class for the Nekolu Spring Boot application. (~129 tok)

## src/main/java/com/zademy/nekolu/config/

- `CacheConfig.java` — Caffeine cache configuration for file metadata. (~445 tok)
- `MetricsConfig.java` — Registers custom Micrometer metrics for TDLib operations. (~604 tok)
- `OpenApiConfig.java` — Configures the generated OpenAPI document exposed through SpringDoc. (~694 tok)
- `RequestCorrelationFilter.java` — Servlet filter that injects a unique request correlation ID into the MDC context. (~526 tok)
- `SetupRedirectInterceptor.java` — Sends web visitors to the first-run authentication wizard while no (~335 tok)
- `TelegramConfig.java` — Centralizes application properties required to bootstrap and operate the shared TDLib client. (~1057 tok)
- `TelegramHealthIndicator.java` — Custom health indicator that reports TDLib authorization status, (~744 tok)
- `WebConfig.java` — Web MVC configuration for internationalization support and the first-run (~570 tok)

## src/main/java/com/zademy/nekolu/constants/

- `FileTypeConstants.java` — Centralizes logical file-type identifiers used by the API, filters, and user interface. (~483 tok)
- `MediaConstants.java` — Defines shared MIME types, filename prefixes, and file extensions for media handling. (~777 tok)
- `ServiceDefaults.java` — Holds shared default values and timeout settings used by service-layer workflows. (~222 tok)

## src/main/java/com/zademy/nekolu/controller/

- `FileController.java` — Exposes REST endpoints for browsing, uploading, downloading, previewing, and organizing Telegram-bac (~9478 tok)
- `GlobalExceptionHandler.java` — Centralizes exception handling for all REST controllers. (~2294 tok)
- `SetupController.java` — First-run authentication wizard: shows the step TDLib is waiting for and (~1010 tok)
- `TelegramController.java` — Exposes folder-management endpoints backed by Telegram private channels. (~1952 tok)
- `WebController.java` — MVC controller for Thymeleaf views. (~366 tok)

## src/main/java/com/zademy/nekolu/dto/

- `ApiErrorResponse.java` — Standard error response following RFC 7807 Problem Details conventions. (~308 tok)
- `BulkDeleteRequest.java` — Request payload for deleting multiple Telegram file messages in a single operation. (~445 tok)
- `BulkDeleteResponse.java` — Response payload that summarizes the outcome of a bulk delete operation. (~380 tok)
- `CreateFolderRequest.java` — Request for creating a new folder (private Telegram channel). (~251 tok)
- `CreateFolderResponse.java` — Response after creating a folder. (~272 tok)
- `DeleteFolderResponse.java` — Response after deleting a folder. (~215 tok)
- `DeleteMessageRequest.java` — Request payload for deleting a single Telegram file message. (~285 tok)
- `DeleteMessageResponse.java` — Response payload returned after attempting to delete a single Telegram message. (~329 tok)
- `DownloadFilesRequest.java` — Request for downloading multiple files. (~181 tok)
- `DownloadJob.java` — DTO for batch download jobs. (~551 tok)
- `DownloadResponse.java` — File download status response. (~611 tok)
- `FileExportResponse.java` — DTO for file export. (~198 tok)
- `FileInfoResponse.java` — Detailed API representation of a Telegram-backed file, including TDLib metadata and logical drive me (~1772 tok)
- `FileStatsResponse.java` — DTO for file statistics. (~277 tok)
- `FileStreamResponse.java` — DTO for file streaming response. (~255 tok)
- `FolderInfo.java` — Information about a folder (private Telegram channel). (~229 tok)
- `FullStatsResponse.java` — DTO for full statistics: files, storage, network, and limits. (~202 tok)
- `NetworkStatsResponse.java` — DTO for TDLib network usage statistics. (~492 tok)
- `StorageStatsResponse.java` — DTO for TDLib local storage statistics. (~238 tok)
- `TelegramLimitsResponse.java` — DTO for Telegram account limits. (~211 tok)
- `UploadCommand.java` — The single upload command: everything an upload needs, travelling as one (~298 tok)
- `UploadResponse.java` — File upload status response for Telegram. (~745 tok)

## src/main/java/com/zademy/nekolu/exception/

- `Exceptions.java` — Single unwrapping point for futures-completed exceptions: async stages (~196 tok)
- `StagingException.java` — Server-side staging failure (disk I/O while materializing an upload). (~125 tok)
- `TelegramNotFoundException.java` — The requested Telegram resource (message, file) does not exist or, when (~130 tok)
- `TelegramNotInitializedException.java` — The Telegram module has no TDLib client yet (startup not finished). (~129 tok)
- `TelegramOperationException.java` — Telegram (upstream) rejected or failed an operation, reported by TDLib (~183 tok)
- `TelegramUnauthorizedException.java` — The TDLib session exists but is not authenticated. The user must (~179 tok)

## src/main/java/com/zademy/nekolu/model/

- `TelegramFileMessage.java` — Domain view of a Telegram message that carries a file, as exposed through (~493 tok)
- `TelegramFileState.java` — Domain view of a file's local download state as exposed through the (~379 tok)

## src/main/java/com/zademy/nekolu/service/

- `FileService.java` — Interface for the Telegram file management service. (~2195 tok)
- `TelegramService.java` — The seam between the workspace and Telegram. This contract owns the whole (~2842 tok)

## src/main/java/com/zademy/nekolu/service/impl/

- `FileServiceImpl.java` — Implementation of the Telegram file management service. (~11209 tok)
- `TdLibPreconditions.java` — Centralizes TDLib precondition checks that are repeated across service methods. (~481 tok)
- `TelegramRateLimiter.java` — Token-bucket-style rate limiter that protects the TDLib client from (~644 tok)
- `TelegramServiceImpl.java` — TDLib-backed implementation of Telegram operations such as authorization, downloads, folders, and te (~11191 tok)
- `UploadStagingArea.java` — Deep module owning the staged-upload cycle: materializing an incoming (~1408 tok)

## src/main/resources/

- `application.example.properties` — Spring Boot application name (~734 tok)
- `application.properties` — Spring Boot application name (~712 tok)
- `logback-spring.xml` (~279 tok)
- `messages_es.properties` — === Mensajes en Español === (~535 tok)
- `messages.properties` — === Default messages (English) === (~492 tok)

## src/main/resources/static/css/

- `app.css` — Styles: 89 rules, 20 vars (~9229 tok)
- `tokens.css` — Styles: 58 vars (~708 tok)

## src/main/resources/static/js/

- `api-client.js` — Author: Zademy (~1999 tok)
- `app.js` — Author: Zademy (~1954 tok)
- `grid-table.js` — Author: Zademy (~1998 tok)
- `theme.js` — Declares apply (~190 tok)

## src/main/resources/static/vendors/dropzone/

- `dropzone.css` — Styles: 57 rules, 3 animations (~3370 tok)
- `dropzone.js` (~77402 tok)

## src/main/resources/templates/

- `dashboard.html` — Home (~3853 tok)
- `files.html` — Files (~12158 tok)
- `folder-files.html` — Folder files (~12598 tok)
- `folders.html` — Folders (~4094 tok)
- `setup.html` — Nekolu setup (~1843 tok)
- `stats.html` — Statistics (~5154 tok)

## src/main/resources/templates/layout/

- `base.html` — Nekolu (~1270 tok)

## src/test/java/com/zademy/nekolu/

- `ApplicationTests.java` — Class: ApplicationTests (~405 tok)

## src/test/java/com/zademy/nekolu/controller/

- `FileControllerTest.java` — HTTP error-contract tests over standalone MockMvc: the controllers are (~1705 tok)
- `SetupControllerTest.java` — First-run wizard tests over standalone MockMvc with the in-memory seam (~1985 tok)

## src/test/java/com/zademy/nekolu/service/

- `FakeTelegramService.java` — In-memory adapter for the TelegramService seam, used by unit tests of the (~3133 tok)
- `FakeTelegramServiceTest.java` — Contract tests for the file-message operations of the TelegramService seam, (~3411 tok)

## src/test/java/com/zademy/nekolu/service/impl/

- `FileServiceImplTest.java` — Unit tests for the file-management module through the TelegramService seam, (~3782 tok)
- `TdLibPreconditionsTest.java` — Unit tests for the centralized TDLib readiness preconditions used by the (~669 tok)
- `UploadStagingAreaTest.java` — Unit tests for the upload staging area against a real temporary (~908 tok)

## tdlib/

- `db.sqlite-shm` (~8738 tok)
- `td.binlog` (~210562 tok)
