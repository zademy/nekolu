<h1 align="center">Nekolu</h1>

<p align="center">
  Telegram-backed personal file workspace built with Spring Boot 4, Java 25, and TDLib.
</p>

<p align="center">
  <a href="https://nekolu.me/">
    <img src="https://img.shields.io/badge/Official%20Site-nekolu.me-0f172a?style=for-the-badge&logo=googlechrome&logoColor=white" alt="Official site">
  </a>
  <a href="./LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-22c55e?style=for-the-badge" alt="MIT License">
  </a>
  <img src="https://img.shields.io/badge/Java-25-f89820?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 25">
  <img src="https://img.shields.io/badge/Spring%20Boot-4-6db33f?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 4">
  <img src="https://img.shields.io/badge/TDLib-Native%20Java-229ed9?style=for-the-badge&logo=telegram&logoColor=white" alt="TDLib Native Java">
  <a href="https://deepwiki.com/zademy/nekolu">
    <img src="https://img.shields.io/badge/DeepWiki-Ask%20AI-5b21b6?style=for-the-badge&logo=googledocs&logoColor=white" alt="Ask DeepWiki">
  </a>
</p>

<p align="center">
  <a href="https://nekolu.me/">Official Website</a>
  ·
  <a href="http://localhost:8080/swagger-ui.html">Swagger UI</a>
  ·
  <a href="http://localhost:8080/v3/api-docs">OpenAPI</a>
</p>

Nekolu is a Spring Boot 4 application that turns Telegram into a personal file workspace.
It uses the native TDLib Java bindings to upload, browse, download, preview, organize, and inspect files stored in **Saved Messages** and Telegram-backed folder channels.

The project exposes:

- a REST API documented with OpenAPI / Swagger UI
- a server-rendered Thymeleaf interface with i18n support
- TDLib-backed file discovery, upload, download, and inline preview workflows
- logical drive features such as virtual paths, archive state, and trash operations
- folder management backed by private Telegram channels
- real-time download progress via WebSocket
- observability through Actuator, Micrometer metrics, and Prometheus

---

## Core capabilities

### File management
- List files with filters, sorting, pagination, and folder scoping
- Upload documents and photos to Telegram
- Download files on demand
- View downloaded media inline in the browser
- Generate thumbnails and stream metadata
- Run batch downloads and inspect progress

### Logical drive features
- Assign virtual paths to files
- Archive and restore files
- Move files between logical paths
- Send files to logical trash and restore them later
- Keep logical metadata alongside raw Telegram metadata

### Folder management
- Create folders backed by private Telegram channels
- List folders
- Delete folders
- List files inside a specific folder channel

### Observability
- OpenAPI / Swagger UI documentation
- Spring Boot Actuator with custom TDLib + disk health indicators
- Micrometer metrics with Prometheus exporter (downloads, uploads, folders, errors)
- Caffeine caches for file metadata
- Structured logging with request correlation IDs (MDC)
- TDLib-backed statistics for files, storage, and network activity

### Resilience
- Global exception handler with consistent error responses
- Bean Validation on all request DTOs
- Semaphore-based rate limiter for TDLib operations
- Centralized TDLib precondition checks

---

## Technology stack

| Layer | Technology |
|-------|-----------|
| Language | Java 25 |
| Framework | Spring Boot 4.0.5 |
| Web | Spring MVC + Thymeleaf + Layout Dialect |
| Telegram | TDLib native Java bindings (`org.drinkless:tdlib`) |
| API Docs | SpringDoc OpenAPI 3.0 |
| Validation | Jakarta Bean Validation |
| Caching | Caffeine |
| Metrics | Micrometer + Prometheus |
| Real-time | WebSocket |
| Logging | Logback with JSON output (prod) + MDC correlation |
| i18n | Spring MessageSource (English, Spanish) |
| Build | Maven |
| Container | Docker + Docker Compose |
| Frontend | Vanilla JavaScript + Tailwind CSS 4 |

---

## Runtime requirements

- Java 25
- Maven 3.9+
- A Telegram application created at [my.telegram.org](https://my.telegram.org)
- A valid TDLib runtime environment with the native library available
- A previously authenticated TDLib session in the configured `tdlib/` directory

### Important TDLib constraint

TDLib does **not** allow multiple processes to use the same database directory at the same time. Do not run the TDLib example client and Nekolu simultaneously against the same directory, and do not start two Nekolu instances sharing the same TDLib database.

---

## Getting started

### 1. Create Telegram API credentials

Go to [my.telegram.org/apps](https://my.telegram.org/apps) and create an application. You will need:

- your `api_id`
- your `api_hash`
- your Telegram numeric user ID

To find your numeric user ID, you can use Telegram Desktop, a TDLib script calling `getMe`, or a trusted Telegram utility.

### 2. Configure the application

Copy the example properties file and fill in your values:

```bash
cp src/main/resources/application.example.properties src/main/resources/application.properties
```

Use environment variables to provide sensitive values instead of hardcoding them:

```bash
export TELEGRAM_API_ID=<your_api_id>
export TELEGRAM_API_HASH=<your_api_hash>
export TELEGRAM_USER_ID=<your_numeric_user_id>
```

> **Never commit `application.properties` with real credentials to version control.**

### 3. Authenticate TDLib (first run only)

Nekolu does **not** provide its own Telegram login flow. It expects an already authorized TDLib session in the configured `tdlib/` directory.

From the project root, run:

```bash
java --enable-native-access=ALL-UNNAMED -Djava.library.path=lib -cp lib/tdlib.jar org.drinkless.tdlib.example.Example
```

Follow the interactive prompts to complete authentication (phone number, verification code, and 2FA password if enabled). After successful login, quit the example client before starting Nekolu.

### 4. Run the application

**With Maven:**

```bash
mvn spring-boot:run
```

**As a packaged JAR:**

```bash
mvn clean package -DskipTests
java -jar target/nekolu-1.0.0.jar
```

**With Docker Compose:**

```bash
docker compose up --build
```

The application starts at [http://localhost:8080](http://localhost:8080).

---

## Configuration reference

The main configuration file is `src/main/resources/application.properties`. See `application.example.properties` for all available options.

### Key configuration areas

| Area | Properties prefix | Description |
|------|-------------------|-------------|
| Telegram credentials | `telegram.api.*`, `telegram.user.id` | API ID, hash, and user ID (use env vars) |
| TDLib directories | `telegram.database-directory`, `telegram.files-directory` | Session state, file cache, SQLite metadata |
| TDLib features | `telegram.use-*` | Feature flags for message/chat/file databases |
| Upload limits | `spring.servlet.multipart.*` | Max file and request sizes (default: 2 GB) |
| Async timeout | `spring.mvc.async.request-timeout` | Timeout for async MVC requests (default: 5 min) |
| Rate limiter | `nekolu.rate-limit.*` | Max concurrent TDLib operations and acquire timeout |
| Actuator | `management.endpoints.*` | Exposed health, metrics, and Prometheus endpoints |

---

## Docker

The project includes a multi-stage `Dockerfile` and a `docker-compose.yml`.

```bash
docker compose up --build
```

Environment variables for Telegram credentials are passed through the compose file. TDLib session data is persisted in a named volume (`tdlib-data`).

> **Note:** The Dockerfile expects a Linux-compatible TDLib native library (`libtdjni.so`) in the `libs/` directory. Adjust the path if your setup differs.

---

## API documentation

Once running, the API documentation is available at:

- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Developer reference:** `documentation.md`

### API areas

| Area | Base path | Capabilities |
|------|-----------|-------------|
| Files | `/api/telegram/files` | List, download, upload, preview, thumbnails, batch operations, archive, move, trash, delete, export |
| Folders | `/api/telegram` | Create, list, delete folder channels |
| Progress | `/ws/download-progress` | WebSocket endpoint for real-time download progress |
| Health | `/actuator/health` | TDLib authorization, disk space, directory accessibility |
| Metrics | `/actuator/prometheus` | Prometheus-compatible metrics endpoint |

> The OpenAPI definition is the source of truth for request and response contracts.

---

## Web interface

The Thymeleaf UI includes the following screens:

- **Dashboard** — overview and quick actions
- **Files** — file listing, search, upload, and inline preview
- **Folders** — folder management
- **Folder Files** — files inside a specific folder channel
- **Statistics** — TDLib storage, network, and account limits

### UI behavior notes

- File tables and grids are backed by the REST API
- Inline media preview is available only when a file is fully downloaded locally
- Uploads use a staging directory under `tdlib/upload-staging`
- The UI supports language switching via the `lang` query parameter (e.g., `?lang=es`)

---

## Internationalization (i18n)

Nekolu supports multiple languages through Spring MessageSource:

| Locale | File |
|--------|------|
| English (default) | `messages.properties` |
| Spanish | `messages_es.properties` |

The locale is resolved from the `Accept-Language` header or the `lang` query parameter.

---

## Project structure

```text
src/main/java/com/zademy/nekolu/
├── Application.java
├── config/
│   ├── CacheConfig.java
│   ├── MetricsConfig.java
│   ├── OpenApiConfig.java
│   ├── RequestCorrelationFilter.java
│   ├── TelegramConfig.java
│   ├── TelegramHealthIndicator.java
│   ├── WebConfig.java
│   └── WebSocketConfig.java
├── constants/
│   ├── FileTypeConstants.java
│   ├── MediaConstants.java
│   └── ServiceDefaults.java
├── controller/
│   ├── FileController.java
│   ├── GlobalExceptionHandler.java
│   ├── TelegramController.java
│   └── WebController.java
├── dto/
│   ├── ApiErrorResponse.java
│   └── (request and response records)
├── model/
│   └── LogicalFileMetadata.java
├── service/
│   ├── FileService.java
│   ├── MetadataIndexService.java
│   └── TelegramService.java
├── service/impl/
│   ├── FileServiceImpl.java
│   ├── MetadataIndexServiceImpl.java
│   ├── TdLibPreconditions.java
│   ├── TelegramRateLimiter.java
│   ├── TelegramServiceImpl.java
│   └── TemporaryUploadJanitor.java
└── websocket/
    └── DownloadProgressWebSocketHandler.java

src/main/resources/
├── application.example.properties
├── logback-spring.xml
├── messages.properties
├── messages_es.properties
├── static/
│   ├── css/
│   └── js/
└── templates/
    ├── dashboard.html
    ├── files.html
    ├── folder-files.html
    ├── folders.html
    ├── stats.html
    └── layout/

Dockerfile
docker-compose.yml
```

---

## Testing

Run the unit test suite:

```bash
mvn test
```

Compile without tests:

```bash
mvn -DskipTests compile
```

> Integration tests that load the full Spring context require a TDLib native library and a valid Telegram session. Unit tests run independently.

---

## Operational notes

### Download state
A file is considered downloaded only when TDLib reports a completed local copy and that file exists on disk. A local file used only as an upload source is **not** considered a downloaded file.

### Upload staging
Uploads are staged in `tdlib/upload-staging/`. These files exist only to provide TDLib with a stable local path during upload. After TDLib confirms the upload, the staged file is cleaned up. Stale leftovers are removed by the startup janitor.

### Caching
The application uses Caffeine for in-memory metadata caching. This cache is a performance layer only; it is not a source of truth for file download state.

### Logging
In the default profile, logs are written in a human-readable format with request correlation IDs. In the `prod` profile, logs are emitted as structured JSON for log aggregation systems.

Every HTTP request is tagged with an `X-Request-Id` header that appears in all related log entries.

---

## Troubleshooting

### TDLib database lock errors
If you see errors about locking `td.binlog`, another process is already using the same TDLib directory. Stop the other process and restart Nekolu.

### Unauthorized / authentication errors
If Telegram operations fail because the client is not authorized, authenticate TDLib first:

```bash
java --enable-native-access=ALL-UNNAMED -Djava.library.path=lib -cp lib/tdlib.jar org.drinkless.tdlib.example.Example
```

After successful authentication, quit the TDLib example client, ensure the same `tdlib/` directory is configured, and restart Nekolu.

### `PHONE_NUMBER_INVALID`
Re-enter the phone number in international format with the leading `+` and country code.

### File can be listed but not viewed inline
Inline preview requires a fully downloaded local copy. If the file is still pending, trigger a download first.


---

## Notes for maintainers

- Keep all public-facing API documentation in English
- Prefer updating OpenAPI annotations when changing request/response contracts
- Keep this README aligned with the actual screens, modules, and endpoints in the codebase
- Never commit real Telegram credentials or session data to version control

---

## Support Development

If Nekolu is useful to you, consider buying me a coffee!

<a href="https://ko-fi.com/C0C01Y1SQI" target="_blank"><img height="26" src="https://img.shields.io/badge/Donate-Ko--fi-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white" alt="Donate with Ko-fi" /></a>
<a href="https://buy.stripe.com/00wcN67J46kl8LY8GYfMA01" target="_blank"><img height="26" src="https://img.shields.io/badge/Donate-Stripe-635bff?style=for-the-badge&logo=stripe&logoColor=white" alt="Donate with Stripe" /></a>
