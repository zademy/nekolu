<h1 align="center">Nekolu</h1>

<p align="center">
  A self-hosted personal file workspace that uses Telegram as its storage backend.
  Built with Spring Boot 4, Java 25, and TDLib.
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
  <img src="https://img.shields.io/badge/TDLib-1.8.66-229ed9?style=for-the-badge&logo=telegram&logoColor=white" alt="TDLib 1.8.66">
  <a href="https://deepwiki.com/zademy/nekolu">
    <img src="https://img.shields.io/badge/DeepWiki-Ask%20AI-5b21b6?style=for-the-badge&logo=googledocs&logoColor=white" alt="Ask DeepWiki">
  </a>
</p>

---

https://github.com/user-attachments/assets/802d8d7d-5b2c-41d1-bd57-55e12f51819b

---

## What is Nekolu?

Nekolu turns a Telegram account into a private cloud drive. Files are stored as Telegram messages in your own chat (Saved Messages) or in private channels that Nekolu manages as folders. You upload, browse, search, preview, download, and organize files through a web interface — the Telegram app is the storage, Nekolu is the workspace on top.

### Key features

- **Upload from the web** — drag and drop files; they appear in your Telegram and in the workspace with their original filenames
- **Browse and search** — filter by type (photo, video, audio, document, voice, video note), date range, file size, and name; sort by date, size, or name
- **Folders** — private Telegram channels managed as workspace folders
- **Download and preview** — inline preview for downloaded media, on-demand download from Telegram with progress polling
- **First-run wizard** — authenticate with your phone number, verification code, and 2FA password directly in the web UI
- **Light and dark themes** — GitHub Primer design tokens, light by default, dark with a persistent toggle
- **REST API** — full OpenAPI/Swagger documentation
- **Docker** — one-command deployment with persistent TDLib session

---

## Technology stack

| Layer | Technology |
|-------|-----------|
| Language | Java 25 |
| Framework | Spring Boot 4.1.1 |
| Telegram | TDLib 1.8.66 (native Java bindings) |
| Web | Spring MVC + Thymeleaf (server-side rendered) |
| Design | GitHub Primer tokens (light/dark) + Tailwind CSS 4 |
| API Docs | SpringDoc OpenAPI 3.0 |
| Caching | Caffeine |
| Metrics | Micrometer + Prometheus |
| Logging | Logback (JSON in prod) + MDC correlation IDs |
| i18n | Spring MessageSource (English, Spanish) |
| Build | Maven |
| Container | Docker + Docker Compose |
| Tests | JUnit 5 (68 unit tests, no TDLib required) |

---

## Getting started

### Prerequisites

- **Java 25+** (JDK for building, JRE for running)
- **Maven 3.9+** (or use the included Maven wrapper `./mvnw`)
- A **Telegram application** created at [my.telegram.org](https://my.telegram.org) — you need your `api_id` and `api_hash`
- **Docker** (optional, for containerized deployment)

### 1. Get Telegram API credentials

Go to [my.telegram.org/apps](https://my.telegram.org/apps) and create an application. You will receive:

- `api_id` — a numeric identifier
- `api_hash` — a hexadecimal string

> **Never commit real credentials.** Use environment variables or the `.env` file (gitignored).

### 2. Configure the application

```bash
cp src/main/resources/application.example.properties src/main/resources/application.properties
```

Or use environment variables:

```bash
export TELEGRAM_API_ID=12345678
export TELEGRAM_API_HASH=abcdef1234567890abcdef1234567890
```

<details>
<summary>View all configuration properties</summary>

```properties
# Required
telegram.api.id=12345678
telegram.api.hash=your_api_hash_here

# Optional (auto-resolved via TDLib GetMe on first run)
telegram.user.id=0

# TDLib directories (session persists here)
telegram.database-directory=./tdlib
telegram.files-directory=./tdlib

# Upload staging (configurable)
nekolu.upload-staging-directory=tdlib/upload-staging

# System metadata (reported to Telegram)
telegram.system.device-model=Nekolu Server
telegram.app.version=1.0
```

</details>

### 3. Run the application

**With Maven (local development):**

```bash
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`.

**With Docker:**

```bash
cp .env.example .env    # Fill in your credentials
docker compose up -d
```

The app starts on `http://localhost:8080` with a health check and persistent TDLib session volume.

### 4. Authenticate (first run only)

On first visit, Nekolu redirects to `/setup` — a three-step wizard:

1. **Phone number** — enter your number in international format (e.g., `+1234567890`)
2. **Verification code** — enter the code Telegram sends you
3. **Two-step verification** — enter your cloud password (only if you have 2FA enabled)

After authentication, the session persists in the `tdlib/` directory. You won't need to authenticate again unless you delete the directory.

> **Alternative:** You can also authenticate with the TDLib CLI example client. See [Troubleshooting](#troubleshooting) for details.

---

## Usage

### Web interface

| Page | Path | Description |
|------|------|-------------|
| Dashboard | `/` | Overview with file counts, storage stats, and recent uploads |
| Files | `/files` | Browse, search, filter, upload, download, and manage files |
| Folders | `/folders` | Create and manage folder channels |
| Statistics | `/stats` | Storage, network, and Telegram account statistics |
| Setup | `/setup` | First-run authentication wizard (shown when not authenticated) |

### REST API

The full API is documented at `http://localhost:8080/swagger-ui.html` (Swagger UI) and `http://localhost:8080/v3/api-docs` (OpenAPI JSON).

Key endpoints:

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/telegram/files` | List files with filters and sorting |
| `GET` | `/api/telegram/files/{id}` | Get file metadata |
| `POST` | `/api/telegram/files/upload` | Upload a file (multipart) |
| `POST` | `/api/telegram/files/{id}/download` | Start a download |
| `DELETE` | `/api/telegram/files/message` | Delete a file message |
| `GET` | `/api/telegram/folders` | List folders |
| `POST` | `/api/telegram/folders` | Create a folder |

Error responses use typed HTTP statuses:

| Status | Meaning |
|--------|---------|
| 401 | Session not authenticated |
| 404 | File or resource not found |
| 502 | Telegram rejected the operation |
| 503 | TDLib module not initialized |
| 504 | Operation timed out |

---

## Architecture

### Module layout

```
src/main/java/com/zademy/nekolu/
├── Application.java                 # Spring Boot entry point
├── config/
│   ├── CacheConfig.java             # Caffeine cache definitions
│   ├── MetricsConfig.java           # Micrometer metrics
│   ├── OpenApiConfig.java           # SpringDoc setup
│   ├── TelegramConfig.java          # TDLib connection properties
│   ├── TelegramHealthIndicator.java # /actuator/health TDLib check
│   ├── SetupRedirectInterceptor.java # Redirects to /setup when unauthenticated
│   └── WebConfig.java               # MVC + i18n + interceptors
├── controller/
│   ├── FileController.java          # /api/telegram/files endpoints
│   ├── GlobalExceptionHandler.java  # Typed error responses
│   ├── SetupController.java         # First-run wizard (/setup)
│   ├── TelegramController.java      # /api/telegram/folders endpoints
│   └── WebController.java           # Thymeleaf page routes
├── dto/                             # Request/response records
├── exception/                       # Typed exceptions + shared helpers
├── model/
│   ├── TelegramFileMessage.java     # Domain: file message from the seam
│   └── TelegramFileState.java      # Domain: download state
└── service/
    ├── FileService.java             # Interface: file operations
    ├── TelegramService.java         # Interface: Telegram seam
    └── impl/
        ├── FileServiceImpl.java     # Workspace logic
        ├── TelegramServiceImpl.java # TDLib protocol (all TDLib lives here)
        ├── UploadStagingArea.java   # Upload staging lifecycle
        ├── TelegramRateLimiter.java # Semaphore rate limiter
        └── TdLibPreconditions.java  # Readiness checks
```

### The Telegram seam

All TDLib interaction is confined to `TelegramServiceImpl` behind the `TelegramService` interface. The seam speaks in domain types (`TelegramFileMessage`, `TelegramFileState`) — no TDLib types leak to the rest of the codebase. A request guard applies rate limiting, readiness preconditions, and a 30-second timeout to every operation.

Two adapters satisfy the seam:
- **`TelegramServiceImpl`** — the real TDLib adapter (production)
- **`FakeTelegramService`** — an in-memory adapter (tests, no TDLib needed)

This is why the 68 unit tests run without TDLib, Spring context, or a Telegram session.

### Upload staging

Uploads are staged in `tdlib/upload-staging/{uuid}/` where `{uuid}` is a unique directory per upload. The file inside carries the original (sanitized) filename — Telegram derives the visible name from the path, so users see `report.pdf`, not a prefixed name. Failed uploads discard the whole directory; stale leftovers are purged on startup.

### Error handling

The `GlobalExceptionHandler` maps typed exceptions to HTTP statuses with consistent `ApiErrorResponse` bodies:

| Exception | HTTP | When |
|-----------|------|------|
| `TelegramUnauthorizedException` | 401 | Session not authenticated |
| `TelegramNotInitializedException` | 503 | TDLib module not started |
| `TelegramOperationException` | 502 | Telegram rejected the request |
| `TelegramNotFoundException` | 404 | Resource doesn't exist |
| `TimeoutException` | 504 | Request timed out |

### Design system

The UI uses GitHub Primer design tokens (the same color system as GitHub.com). Light theme is the default; dark theme is available via a toggle in the topbar (persisted in `localStorage`, respects `prefers-color-scheme` on first visit). All colors live in `static/css/tokens.css` — the only file allowed to contain color literals.

---

## Testing

```bash
# Full suite (68 tests, no TDLib needed)
./mvnw test

# Compile only
./mvnw compile -DskipTests

# Run a specific test class
./mvnw test -Dtest=FileServiceImplTest
```

Unit tests use `FakeTelegramService` (an in-memory adapter that satisfies the `TelegramService` seam). They cover search, download, upload, deletion, and error modes — all without TDLib, a Spring context, or a Telegram session.

The `ApplicationTests` class loads the full Spring context and requires a real TDLib session. It runs automatically when a session exists in `tdlib/`.

---

## Docker deployment

### Quick start

```bash
# 1. Copy the environment template
cp .env.example .env

# 2. Fill in your Telegram credentials
#    (use mock values for testing, real values for production)
echo 'TELEGRAM_API_ID=12345678' >> .env
echo 'TELEGRAM_API_HASH=your_hash_here' >> .env

# 3. Build and run
docker compose up -d
```

### What Docker provides

- Multi-stage build: Maven build → slim JRE runtime
- TDLib native library for Linux (`lib/libtdjni.so`) included
- Persistent TDLib session volume (`tdlib-data`)
- Health check (`/actuator/health`)
- Automatic restart on failure

### Docker files

| File | Purpose |
|------|---------|
| `Dockerfile` | Multi-stage build (builder + runtime) |
| `docker-compose.yml` | Service definition with volumes and health check |
| `.env.example` | Template for environment variables |
| `.dockerignore` | Excludes docker/, .git, tdlib/, target/, .env from build context |

> The `lib/` directory contains platform-specific TDLib binaries: `tdlib.jar` + `libtdjni.dylib` for macOS, `tdlib-linux.jar` + `libtdjni.so` for Docker/Linux. Both pairs must come from the same compilation — mismatched pairs crash with a JNI version error.

---

## Project structure

```
nekolu/
├── src/main/java/com/zademy/nekolu/    # Java source (see Architecture above)
├── src/main/resources/
│   ├── application.example.properties   # Configuration template
│   ├── messages.properties              # English i18n
│   ├── messages_es.properties           # Spanish i18n
│   ├── static/
│   │   ├── css/tokens.css              # Primer design tokens (light/dark)
│   │   ├── css/app.css                 # Application styles
│   │   └── js/                         # Frontend JavaScript
│   └── templates/                      # Thymeleaf HTML templates
├── src/test/java/                       # Unit tests (no TDLib needed)
├── lib/                                 # TDLib binaries (platform-specific)
├── CONTEXT.md                           # Domain glossary
├── docs/adr/                            # Architecture Decision Records
├── docs/agents/                         # Agent workflow configuration
├── Dockerfile
├── docker-compose.yml
├── .env.example                         # Environment variable template
└── pom.xml
```

---

## Troubleshooting

### TDLib database lock errors
TDLib allows only one process per database directory. Stop any other Nekolu instance or TDLib client using the same `tdlib/` directory before starting.

### Authentication errors
If you see 401 errors, authenticate via the built-in wizard at `/setup`, or use the TDLib CLI:

```bash
java --enable-native-access=ALL-UNNAMED \
  -Djava.library.path=lib \
  -cp lib/tdlib.jar \
  org.drinkless.tdlib.example.Example
```

Follow the prompts (phone, code, 2FA), then quit the example client and start Nekolu.

### `PHONE_NUMBER_INVALID`
Enter the phone number in international format with the leading `+` and country code (e.g., `+1234567890`).

### File listed but not previewable
Inline preview requires a fully downloaded local copy. Trigger a download first, then refresh.

### Docker container crashes with `Mismatched TdApi.java`
The `lib/tdlib.jar` and `lib/libtdjni.so` must come from the same TDLib compilation. If you update one, update both.

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Run the tests (`./mvnw test`) — all 68 must pass
4. Commit your changes
5. Open a Pull Request

### Guidelines

- Keep all public-facing API documentation in English
- Update OpenAPI annotations when changing request/response contracts
- Run `./mvnw test` before submitting — 68/68 must pass
- Never commit real Telegram credentials, session data, or API keys
- Keep this README aligned with the actual codebase

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## Support Development

If Nekolu is useful to you, consider buying me a coffee!

<a href="https://ko-fi.com/C0C01Y1SQI" target="_blank"><img height="26" src="https://img.shields.io/badge/Donate-Ko--fi-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white" alt="Donate with Ko-fi" /></a>
<a href="https://buy.stripe.com/00wcN67J46kl8LY8GYfMA01" target="_blank"><img height="26" src="https://img.shields.io/badge/Donate-Stripe-635bff?style=for-the-badge&logo=stripe&logoColor=white" alt="Donate with Stripe" /></a>
