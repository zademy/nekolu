# Security Policy

## Supported versions

Security fixes are handled on a best-effort basis for:

| Version | Supported |
| --- | --- |
| Current default branch | Yes |
| Most recent tagged release | Yes |
| Older releases | No |

## Reporting a vulnerability

This project uses repository-based reporting only.

Preferred path:

1. Use GitHub's private vulnerability reporting or Security tab for this repository, if it is enabled.
2. Include the affected commit, version, configuration, impact, and reproduction steps.
3. Share only what is needed to validate the issue.

Please do **not** open a public issue for an undisclosed vulnerability.

If private vulnerability reporting is not available yet:

- open a minimal public issue without exploit details
- describe the area affected and ask maintainers to continue through a repository-native secure flow when available
- wait to publish proof-of-concept details until a fix or mitigation exists

For non-sensitive hardening suggestions, defensive improvements, or documentation gaps, a normal public issue is fine.

## What to expect

Maintainers will review reports on a best-effort basis. This project does not provide a paid support channel, response-time guarantee, or formal SLA.

When a report is confirmed, maintainers may:

- prepare a fix on the active development branch
- document mitigations or workarounds
- publish a release when appropriate
- credit the reporter if the reporter wants that

## Project-specific security guidance

Because Nekolu works with Telegram sessions, local TDLib state, and downloaded files, please take extra care with:

- `api_id`, `api_hash`, session files, and environment variables
- local `tdlib/` directories and any file cache inside them
- logs, screenshots, and bug reports that may expose chat names, file metadata, or local paths
- uploaded and downloaded files that may contain private, copyrighted, or sensitive content

TDLib-specific note:

- Do not run multiple processes against the same TDLib database directory. Shared access can corrupt state, break sessions, or expose data unexpectedly.

## Secure deployment reminders

- keep secrets out of the repository
- use environment variables or local untracked configuration for credentials
- review logs before sharing them publicly
- run the application with the least host access it needs
- keep your Java runtime, OS, and dependencies updated

## Responsible use and liability

Nekolu is released under the MIT License and is intended for lawful, authorized use.

Each operator is responsible for:

- their own deployment and infrastructure
- protecting their credentials and local data
- complying with applicable law
- complying with Telegram terms and third-party rights
- reviewing the content they store, upload, download, or share

The maintainers do not endorse, authorize, or accept responsibility for illegal, abusive, infringing, or otherwise unauthorized use of the software by end users.
