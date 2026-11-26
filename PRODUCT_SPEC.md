# Stash — Product and technical specification

## Vision
Replace Telegram Saved Messages with a personal knowledge inbox. Save instantly, organize later, find anything. Capture must not require opening or navigating the main application. Target local durable capture within two seconds, measured rather than assumed.

## Product requirements
- Android, iOS, macOS, Windows, Linux and web; synchronized accounts with a self-hostable backend. Personal use first, multi-user isolation from day one; commercial model undecided.
- Unified items: URLs, plain text, Markdown notes, code, and eventually images, files, PDFs and video links. Preserve original input and URL; infer type without losing mixed text/link content.
- Inbox by default; collections, tags, favorites, read later, archive, trash; no mandatory title or classification. Search, filter, sort and duplicate suggestions; never silently discard duplicates.
- Adaptive UI: chronological mobile feed; desktop/web sidebar, item list and detail/reader pane. Light/dark themes, accessible keyboard navigation, responsive layouts.
- Reader: sanitized extracted article, original link, offline snapshot, font/size/spacing/theme controls, reading progress. Later highlights and annotations. Handle extraction failure, paywalls and authenticated pages gracefully; do not bypass access controls.
- Export JSON and Markdown; import Telegram Desktop JSON export with preview, deduplication and error reporting. Later attachments and browser bookmark import.

## Capture UX (highest priority)
Instant mode: explicit action saves clipboard directly to Inbox and shows success/undo only after durable persistence. Quick mode: independent small window with focused input, paste, Enter to save (multiline editing supported), Escape to cancel. Detailed mode: optional collection, tags and note. Never wait for metadata or network before acknowledging local capture. Preserve failed inputs for retry. No continuous clipboard monitoring.
- Desktop: tray/menu bar, configurable global hotkeys for instant clipboard save and quick capture; investigate native implementations and Wayland restrictions. Avoid assuming default shortcuts are conflict-free.
- Android: ACTION_SEND share target, lightweight capture activity; later Quick Settings tile, widget, selected-text action.
- iOS: native Share Extension with App Group durable handoff; later App Intents/Shortcuts, Action Button and widgets. Do not assume extensions can run indefinitely or share a database safely without coordination.
- Web: quick-add UI; later browser extension for current page, selected text, context menu and tabs. Browser clipboard permissions apply.
- Telegram: later bot for forwarding content, authenticated account linking and backend capture endpoint. No access to existing Saved Messages history through bot; use export/import.
- Automatic type detection and domain/MIME-based categorization; user rules later. AI optional, not required.

## Architecture (proposed defaults; validate compatibility before committing)
Kotlin Multiplatform, Compose Multiplatform, Android/iOS/JVM desktop/Wasm web (JS fallback if supported and needed). Coroutines, Flow, Kotlinx Serialization, Ktor Client, Koin, Navigation Compose. Room KMP for supported native targets, IndexedDB adapter for web; evaluate SQLDelight if it materially simplifies persistence. Shared repository contracts and domain logic, platform adapters for native capture and persistence. Ktor server, PostgreSQL, Exposed, Flyway, PostgreSQL full-text search, S3-compatible storage when attachments arrive, Docker Compose and GitHub Actions. OIDC/OAuth using established libraries; secure platform token storage and appropriate browser session handling. Verify current versions and target support against official documentation; no speculative dependencies.

Modular monolith; avoid microservices, Kafka, Redis, Kubernetes and excessive Gradle modules. Suggested logical directories: app/{android,ios,desktop,web}, shared/{domain,data,database,network,sync,ui,designsystem}, contracts, server/{auth,items,sync,extraction,storage}, extensions/{browser,telegram}, deployment. Adapt to actual KMP tooling and keep module count small.

## Domain and capture contracts
Item: client-generated UUID, owner (server-enforced), title, typed content, original input, optional collection, tags, favorite/archive/read-later state, optional reading progress, created/updated timestamps, server revision and deletion state. Typed content: Link(url, metadata, optional article snapshot reference), Note(markdown), Attachment(file reference, MIME). Keep network DTOs and persistence models separate where useful. CaptureRequest(content, source, optional collection/tags/note); CaptureService.capture returns durable result or actionable error. All native entry points use shared capture pipeline; remote integrations use authenticated server capture API. Server owns enrichment, clients consume status/results. Do not block capture on enrichment.

## Offline-first and synchronization
Local database is UI source of truth. Atomically persist item and outbox mutation. Push idempotent mutation IDs with base revision; pull ordered changes via opaque server cursor. Server validates ownership and revisions, commits changes and change log transactionally. Apply pulled changes and cursor atomically. Retry transient failures with bounded exponential backoff and jitter; distinguish permanent failures. Keep tombstones until safe retention policy; support full resync for expired cursors. Preserve both conflicting note edits; explicitly define field merge and delete/edit policy. SSE optional notification hint, not source of truth; pull on reconnect/resume. Attachments need separate resumable lifecycle later. Test offline edits, duplicate retries, concurrent devices, interrupted transactions, cursor paging, conflicts and account switching.

Proposed API: POST /v1/sync/push, GET /v1/sync/pull?cursor=..., GET /v1/items/{id}, GET /v1/items/{id}/content, POST /v1/capture, GET /v1/search?q=..., GET /v1/events (optional), attachment endpoints later. Define contracts and errors before implementation.

## Security and privacy
TLS, tenant isolation, authorization on every resource, secure credentials, redacted logs, backup/restore, export and deletion. Article fetcher must defend against SSRF (private/local addresses, DNS rebinding, redirects), enforce size/time limits and sanitize HTML against XSS; do not execute arbitrary page scripts in initial version. Evaluate Mozilla Readability-compatible extraction or Jsoup-based implementation; Jsoup alone is not a readability algorithm. Do not log clipboard content or tokens. Server-side extraction/search means server can access content; E2EE is an unresolved product decision, not a promised feature. No mandatory AI or external analytics.

## Delivery plan
1. Foundation: buildable KMP skeleton, adaptive UI, local link/note CRUD, Inbox, collections/tags, search, tests; prove desktop and mobile quick capture early and validate web persistence.
2. Sync: backend, database migrations, authentication, outbox/pull, conflict handling, Docker local environment, integration tests.
3. Reader and capture: article extraction, offline snapshots, Android/iOS share integrations, desktop hotkeys/tray, full-text search, Telegram import.
4. Release: platform packaging, backups, CI, browser extension, Telegram bot and additional system shortcuts as appropriate.
P0: quick capture, links/notes, offline storage, sync, search, collections/tags, reader. P1: rules, browser extension, bot, widgets, attachments, reading progress. P2: AI, semantic search, OCR, reminders, change detection, advanced editors and collaboration. No firm schedule until platform spikes complete.

## Acceptance criteria
Capture from another app without navigating library; offline capture survives process restart; metadata failure never loses original; sync converges across two devices with no duplicate mutation effects; search finds notes and URLs; article opens offline after download; user can export data; each supported target has documented build/run steps and tested limitations. Security tests cover ownership and unsafe URLs. Measure capture latency on actual devices.
