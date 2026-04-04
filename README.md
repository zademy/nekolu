# Nekolu

Nekolu is a Spring Boot 4 application that turns Telegram into a personal file workspace.
It uses the native TDLib Java bindings to upload, browse, download, preview, organize, and inspect files stored in **Saved Messages** and Telegram-backed folder channels.

The project exposes:

- a REST API documented with OpenAPI / Swagger UI
- a server-rendered Thymeleaf interface
- TDLib-backed file discovery, upload, download, and inline preview workflows
- logical drive features such as virtual paths, archive state, and trash operations
- folder management backed by private Telegram channels
- statistics, exports, and progress monitoring

---

## Telegram credentials setup

Before running the application, create your Telegram API credentials at [my.telegram.org/apps](https://my.telegram.org/apps).

You will need:

- `api_id`
- `api_hash`
- your Telegram numeric user id

To get `telegram.user.id`, you can use any of these approaches:

- open Telegram Desktop, go to **Saved Messages**, and inspect the account/user id with a Telegram client or utility that exposes numeric ids
- use a temporary TDLib or Telegram API script to call `getMe` and read the returned numeric `id`
- use a trusted Telegram helper bot or tool that shows your numeric user id, then copy that value into `TELEGRAM_USER_ID`

For safe local setup:

- use `src/main/resources/application.example.properties` as the template
- copy it locally to `src/main/resources/application.properties`
- provide real secrets through environment variables instead of committing them to the repository

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

### Observability and support features
- OpenAPI / Swagger UI documentation
- Spring Boot Actuator health endpoint
- Caffeine caches for file metadata
- TDLib-backed statistics for files, storage, and network activity

---

## Technology stack

- **Java 25**
- **Spring Boot 4.0.5**
- **Spring MVC + Thymeleaf**
- **TDLib Java bindings** (`org.drinkless:tdlib`)
- **SpringDoc OpenAPI**
- **Caffeine Cache**
- **Maven**
- **Vanilla JavaScript + Tailwind CSS 4 UI**

---

## Runtime requirements

Before starting the application, make sure the following are available:

- Java 25
- Maven 3.9+
- a Telegram application created at [my.telegram.org](https://my.telegram.org)
- a valid TDLib runtime environment
- a previously authenticated TDLib session stored in the configured `tdlib/` directory

### Important TDLib constraint

TDLib does **not** allow multiple processes to use the same database directory at the same time.

That means:

- do not run the official TDLib example client and this Spring Boot application against the same `tdlib/` directory simultaneously
- do not start two Nekolu instances that share the same TDLib database directory

---

## Configuration

The main configuration file is:

- `src/main/resources/application.properties`

### Telegram credentials

The application supports environment-variable overrides:

```properties
telegram.api.id=${TELEGRAM_API_ID:your_default_api_id}
telegram.api.hash=${TELEGRAM_API_HASH:your_default_api_hash}
telegram.user.id=${TELEGRAM_USER_ID:your_numeric_user_id}
```

Recommended shell setup:

```bash
export TELEGRAM_API_ID=your_api_id
export TELEGRAM_API_HASH=your_api_hash
export TELEGRAM_USER_ID=your_numeric_user_id
```

### TDLib directories

```properties
telegram.database-directory=./tdlib
telegram.files-directory=./tdlib
```

These directories are used for:

- TDLib session state
- local file cache
- SQLite metadata created by TDLib
- application-managed upload staging files

### Async and upload limits

```properties
spring.mvc.async.request-timeout=300000
spring.servlet.multipart.max-file-size=2GB
spring.servlet.multipart.max-request-size=2GB
spring.servlet.multipart.enabled=true
```

---

## Telegram authentication

The application expects TDLib to be authenticated already.
If there is no valid session, API calls that require Telegram access will fail.

A common workflow is:

1. authenticate once with a TDLib client using the same database directory
2. close that client completely
3. start Nekolu using that same TDLib directory

The authenticated session will then be reused by the application.

---

## Running the application

From the project root:

```bash
mvn spring-boot:run
```

Or build first:

```bash
mvn clean package
java -jar target/nekolu-1.0.0.jar
```

Default local URL:

- [http://localhost:8080](http://localhost:8080)

---

## OpenAPI and developer documentation

Once the application is running, the API documentation is available at:

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- Additional developer reference: `documentation.md`

### API areas

#### Files
Base path:

```text
/api/telegram/files
```

Main capabilities:

- list files
- get file metadata
- download content
- view content inline
- fetch thumbnails
- get stream metadata
- subscribe to download progress (SSE)
- start single and batch downloads
- export file lists
- upload files
- archive, move, restore, trash, and delete file messages
- list files inside a folder channel

#### Folders
Base path:

```text
/api/telegram
```

Main capabilities:

- create folder channels
- list folders
- delete folders

> The OpenAPI definition is the source of truth for request and response contracts.

---

## Web interface

The Thymeleaf UI currently includes the following main screens:

- **Dashboard**
- **Files**
- **Folders**
- **Folder Files**
- **Statistics**

### UI behavior notes

- file tables and grids are backed by the REST API
- inline media preview is enabled only when a file is truly downloaded locally
- uploads use an application-controlled staging directory under `tdlib/upload-staging`
- staged upload files are not treated as downloaded files

---

## Project structure

```text
src/main/java/com/zademy/nekolu/
├── Application.java
├── config/
│   ├── CacheConfig.java
│   ├── OpenApiConfig.java
│   └── TelegramConfig.java
├── constants/
│   ├── FileTypeConstants.java
│   ├── MediaConstants.java
│   └── ServiceDefaults.java
├── controller/
│   ├── FileController.java
│   ├── TelegramController.java
│   └── WebController.java
├── dto/
│   ├── request and response records used by REST endpoints
├── model/
│   └── LogicalFileMetadata.java
├── service/
│   ├── FileService.java
│   ├── MetadataIndexService.java
│   └── TelegramService.java
└── service/impl/
    ├── FileServiceImpl.java
    ├── MetadataIndexServiceImpl.java
    ├── TelegramServiceImpl.java
    └── TemporaryUploadJanitor.java

src/main/resources/
├── application.properties
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
```

---

## Testing

Run the full test suite:

```bash
mvn test
```

Compile without tests:

```bash
mvn -DskipTests compile
```

Because TDLib is part of the runtime, some tests may touch files inside the local `tdlib/` directory.
Treat those files as runtime artifacts, not application source code.

---

## Operational notes

### Download state
A file is considered downloaded only when TDLib reports a completed local copy and that file exists on disk.
A local file used only as an upload source is **not** considered a downloaded file.

### Upload staging
Uploads are staged in:

```text
tdlib/upload-staging/
```

These files exist only to provide TDLib with a stable local path during upload.
After TDLib confirms the upload, the staged file is cleaned up and stale leftovers are removed by the startup janitor.

### Caching
The application uses Caffeine for in-memory metadata caching.
This cache is a performance layer only; it is not intended to be a source of truth for file download state.

---

## Troubleshooting

### TDLib database lock errors
If you see errors about locking `td.binlog`, another process is already using the same TDLib directory.
Stop the other process and start the application again.

### Unauthorized / authentication errors
If Telegram operations fail because the client is not authorized, re-authenticate TDLib using the same configured database directory and then restart the application.

### File can be listed but not viewed inline
Inline preview requires a real local downloaded copy.
If a file is still pending, download it first.

---

## Notes for maintainers

- Keep all public-facing API documentation in English
- Prefer updating OpenAPI annotations when changing request/response contracts
- Keep README aligned with the actual screens, modules, and endpoints in the codebase
- Do not document removed modules or deprecated UI features as if they were still active
