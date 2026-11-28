# Stash development plan

Completed increment: **first native offline capture slice (part of 1A/1B with a minimal Inbox)**. The application saves original link/text content with an atomic outbox record, stable retry identity and retained edits. JVM tests, iOS simulator tests, Android compilation and an iOS save/terminate/relaunch check passed. Native persistence also passed the separate repository probes on JVM, iOS simulator and Android emulator. This does not complete every 1A/1B acceptance criterion: process/power interruption during commit, actionable real disk exhaustion, physical-device latency, CI and remaining target coverage still need work.

Status: native capture, basic offline Inbox search, and responsive inline capture increments complete · 2026-11-28. The user has authorized incremental implementation with TDD; this plan does not authorize publishing or releases.

## Read in one minute

1. Prove the build tools and platform boundaries before locking dependencies.
2. Deliver durable offline link/text capture, with a failing test before each behavior.
3. Prove capture from another app early; add organization and export next.
4. Add authenticated synchronization, then the offline reader and import.
5. Package and verify every supported platform before claiming release readiness.

Requirements: [AGENTS.md](../AGENTS.md), [PRODUCT_SPEC.md](../PRODUCT_SPEC.md), [CODEX_START.md](../CODEX_START.md). The original planning-only request was subsequently extended to incremental implementation. Technical decisions are in [architecture.md](architecture.md); inspected tools, sources and unverified compatibility are in [compatibility.md](compatibility.md).

## Working rhythm

Work on one numbered slice at a time. Each slice should fit a small reviewable change. Split a slice if it combines unrelated behavior. Future issue/code work must use a dedicated sub-agent as requested by the user's development instructions, with the outcome reviewed and reported in the current chat.

For each behavior:

1. **Red:** write the smallest meaningful failing acceptance or contract test; run it and confirm the failure is the missing behavior.
2. **Green:** implement just enough behavior to pass, including required error handling.
3. **Refactor:** improve names and ownership while keeping the tests green.
4. **Verify:** run affected integration/platform checks and update build steps, limitations and decisions.
5. **Finish:** commit the slice; report the visible result, exact checks/blockers and one next action.

Use `kotlin.test` and `kotlinx-coroutines-test` with injected clocks, dispatchers and ID/jitter sources. Use explicit completion signals and virtual time; never arbitrary sleeps. Use real persistent databases for transaction/restart/migration claims, real browsers for browser persistence, and real PostgreSQL for server SQL/isolation. Fakes test domain decisions, not storage durability. See the [official coroutine test API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/).

Technical spikes begin with a reproducible pass/fail probe rather than fabricated product tests. Estimates below are investigation budgets for the developer, not delivery dates. Re-estimate after phase 0; there is no fixed release schedule.

## Phase 0 — reduce platform risk

| Slice | Work and first evidence | Exit condition | Initial budget |
| --- | --- | --- | --- |
| **0A — CLI/build feasibility** | Inspect/bootstrap the existing Kotlin Toolchain launcher in an authorized cache; verify Android CLI and official skill availability. In a separate disposable probe, validate Toolchain for shared tests, Room/KSP schema generation, Android instrumentation, iOS extension/App Groups, Wasm resources/workers, desktop native bindings and packaging. | Record exact versions, commands, outputs and unsupported capabilities. Use Toolchain as primary and Gradle as fallback; record blockers before switching. | 1 developer day; stop and record gaps at the limit. |
| **0B — persistence proof** | Test an item + outbox transaction, injected abort, reopen, migration and search on native/JVM storage. Compare IndexedDB with Room 3 web persistence on the same cases, including multiple tabs and quota errors. Evaluate SQLDelight only if it resolves a demonstrated problem. | One persistent adapter passes per target; shared domain does not depend on a database framework. Record why the chosen approach is simplest to maintain. | 1–2 developer days. |
| **0C — external capture proof** | Exercise desktop quick window/tray/hotkey, Android share activity, and iOS Share Extension durable handoff with the library closed. Check macOS, Windows and Linux/X11/Wayland separately. | Acknowledged input survives process termination; denied permission, unavailable shortcut or cancelled extension is handled explicitly. iOS app need not be running. | 2–3 developer days across available hosts/devices. |
| **0D — web/UI proof** | Check Compose Wasm in Chromium, Firefox and Safari: keyboard input, IME, screen-reader semantics, browser navigation, clipboard denial, storage reopen and offline application-shell reload. Verify JS fallback feasibility only if needed. | Document minimum browser/OS/CPU coverage. Web remains a required target; any failure has a tracked resolution, never a silently dropped target. | 1 developer day. |

Build-system decision: the user selected Kotlin Toolchain as primary and Gradle as fallback. Continue 0A checks for actual target builds, tests, Room/KSP, iOS extension integration and packaging. Toolchain is Alpha; document blockers and validate the fallback if it becomes necessary.

Dependency order: 0A precedes implementation probes; 0B and 0C precede committing to the first capture design; 0D precedes locking the web stack. Failed probes lead to a bounded alternative or a documented blocker. Do not invent a passing result for an unavailable host. Ask before a platform/scope reduction or an irreversible decision.

Phase 0 completion requires actual builds/tests on the selected stack, not documentation compatibility alone. Preserve the original requirements if a scaffold generator offers to replace AGENTS.md or initialize Git.

## Phase 1 — useful offline capture

| Slice | Write these failing tests first | Smallest deliverable / acceptance |
| --- | --- | --- |
| **1A — buildable foundation** | A shared behavior test runs on configured targets; platform launch smoke checks are repeatable. | Selected wrapper/configuration, small module layout, Android and iOS shells, desktop and web entry points, exact build/run/test commands and CI skeleton. No speculative feature modules. |
| **1B — durable capture core** | Blank rejection; URL/text/Markdown/code and mixed input preservation; stable IDs on retry; item/outbox rollback; no acknowledgement before commit; commit followed by process restart; actionable disk/quota failure. | Capture to Inbox without title, account/network request or classification. Persistent link/note list. Network/metadata are absent from the capture critical path. Local-only profile is isolated for later account linking. |
| **1C — adaptive Inbox and quick capture** | Feed/list/search update from storage; case/Unicode/URL search; Enter saves once, Shift+Enter inserts a newline, Escape cancels; IME Enter does not submit; denied clipboard access and failed save retain input. | Mobile feed and wide sidebar/list/detail layout, light/dark mode, labeled controls and keyboard focus. Independent desktop quick window and web quick-add; basic offline search. Finish the CODEX_START first vertical slice here. |
| **1D — native capture entry points** | Desktop hotkey collision/permission failure; Android share with app stopped; iOS handoff replay, concurrent writes, termination before/after acknowledgement and account switch; undo while offline/after sync. | Explicit instant clipboard save with success/undo after persistence; desktop tray/menu bar; Android ACTION_SEND capture activity; native iOS Share Extension. Retain durable failed handoffs for retry; no clipboard monitoring. Complete latency checks below. |
| **1E — organize and export** | Collections/tags/state changes survive restart; deleting a collection preserves its items; duplicate suggestion never drops content; trash/restore; JSON/Markdown export preserves original text and offline changes. | Optional detailed capture (collection, tags, note), favorites/read-later/archive/trash, filter/sort, user-controlled duplicate suggestions, export. Foundation milestone complete. |

Do not let UI breadth postpone 1D's capture proof. Bring a blocker back to phase 0 immediately. Initial technical probes are not release-quality share integrations; 1D hardens them with the shared contracts.

## Phase 2 — synchronized accounts

| Slice | Write these failing tests first | Smallest deliverable / acceptance |
| --- | --- | --- |
| **2A — contracts, auth and server persistence** | DTO/error fixtures; two users cannot read/write each other's IDs; forged owner rejected; token/session expiry; account switch cannot expose another account's local state. | Ktor modular monolith, PostgreSQL/Flyway migrations, OIDC through established libraries, secure token adapters and browser session/CSRF handling. Self-hosted development Compose file and example configuration. Local capture still works while signed out or offline. |
| **2B — atomic idempotent push** | Retry after lost response produces one effect; mutation-ID payload mismatch rejected; base-revision mismatch; concurrent writers; server crash between item/change-log/idempotency writes rolls back. | POST /v1/sync/push; authenticated POST /v1/capture reuses server write rules. Account-scoped transactional mutations and change log; clients mark acknowledged outbox work only after local commit. |
| **2C — pull, replay and conflicts** | Ordered paging without missed concurrent commits; item/cursor rollback; duplicate page; expired cursor/full resync preserving outbox; edit/edit and delete/edit; device clock skew; collection/tag conflicts. | GET /v1/sync/pull and item/content reads. Two-device convergence, recoverable note variants and explicit field policies. Tombstone/cursor retention policy documented before cleanup is enabled. |
| **2D — lifecycle and recovery** | Offline edits, transient/permanent errors, 401, 429/Retry-After, interrupted batches, virtual-time backoff/jitter, logout during active sync and reconnect/resume. | Account-owned sync scope, bounded retry, visible sync/conflict state. No login, enrichment or server availability requirement on capture. Optional SSE only after pull reliability is proven. |

Before 2A, record the E2EE product decision because server extraction/search reads content. Before 2C, freeze the tested conflict/retention protocol. These decisions need explicit review; the plan does not promise encryption features or destructive retention behavior.

## Phase 3 — offline reader, search and import

| Slice | Write these failing tests first | Smallest deliverable / acceptance |
| --- | --- | --- |
| **3A — safe enrichment/extraction** | Private/loopback/link-local and metadata-service URLs, IPv6 variants, DNS rebinding/redirects, oversized/slow bodies, hostile HTML/scripts and paywall/auth failure. | Server-owned enrichment with bounded durable jobs; sanitize extracted HTML; preserve original input/link and retriable status. Compare Readability-compatible extraction with a Jsoup-based pipeline on fixtures; Jsoup alone is not the readability algorithm. |
| **3B — reader and local snapshots** | Download interrupted; cached snapshot reopened after restart in airplane mode; corrupted/missing cache; unsafe links; preferences and reading progress persisted. | Explicit snapshot download, local cache manifest, reader typography/theme/spacing controls and original-link action. No access-control bypass. Reading progress is a small follow-up here (P1); highlights/annotations remain later. |
| **3C — full-text search** | Offline notes/URLs/article search, punctuation/code and multilingual content; owner isolation on server search; matching filters and deleted-state behavior. | Native/browser local search supports offline use; PostgreSQL search augments remote queries without becoming required for local results. Document ranking/tokenization differences. |
| **3D — Telegram import** | Realistic Telegram Desktop JSON/text-entity fixtures, preview without writes, duplicates, malformed records, resume/retry and partial failures. | User-reviewed import with explicit deduplication choices, source provenance and error report. Reuse capture/storage rules in bounded batches. A Telegram bot cannot read existing Saved Messages history. |

## Phase 4 — release and follow-ups

| Slice | First evidence / tests | Exit condition |
| --- | --- | --- |
| **4A — packaging and operations** | Install/upgrade and schema migration; backup restored into a clean server; local export recovery; account deletion; secret/log redaction; offline startup after update. | Tested desktop installers on macOS/Windows/Linux, Android/iOS packaging, web HTTPS deployment, pinned CI checks, self-host runbook, TLS and backup/restore instructions. Stash stays a provisional private name; publishing requires a separate request. |
| **4B — P1 capture integrations** | Browser extension permissions, offline capture/retry and account isolation; bot linking/webhook retries; OS entry-point lifecycle. | Separate slices for browser extension, authenticated Telegram bot, Android Quick Settings/widget/selected-text, iOS App Intents/Shortcuts/Action Button/widgets. Current-page/selection/context-menu/tab capture follows browser capabilities. |
| **4C — P1 content extensions** | Attachment resume/checksum/quota/cleanup and offline availability; deterministic rule matching; reading-state synchronization. | Separate attachment lifecycle with later image/file/PDF support and video-link treatment, browser bookmark import, user rules and any remaining reading-progress work. Add S3-compatible storage only when needed. |
| **4D — P2 backlog** | Define acceptance tests when each feature is selected. | Optional AI, semantic search, OCR, reminders, change detection, advanced editors and collaboration. None is required for capture or initial release. |

## Capture performance and durability gate

Start timing at the explicit OS capture action and finish at durable commit plus success feedback. Record cold and warm paths separately, including OS startup/handoff overhead and content size. For quick/detailed mode also report submit-to-commit time separately from user typing time. Use a monotonic clock and sanitized metrics that contain no captured text, URL or credential.

The product target is local durable capture within two seconds on actual devices. Proposed initial reporting: at least 30 cold and 30 warm samples per entry point on representative devices; publish p50, p95, maximum and failures. Use p95 <= 2 seconds as a proposed initial regression gate and investigate every slower sample; do not quietly reinterpret the product target as an average. Validate low-storage/offline/error cases independently. Final device matrix and sample policy are set after the probes.

After each reported success, terminate the relevant process and reopen storage. For iOS, success may mean an App Group envelope is durably saved pending ingestion; replay it exactly once without needing network. Browser success is scoped to committed browser-managed storage; denied persistence/private mode/eviction limitations must be visible. No platform can promise persistence after explicit user data deletion.

## Definition of done for each slice

1. Required behavior has a recorded red/green cycle and meaningful failure-path coverage.
2. Affected available builds/tests pass with exact commands; unavailable targets stay explicitly unverified.
3. Original input, offline recovery, ownership/privacy, keyboard and accessibility behavior are reviewed.
4. Changes include necessary migrations, architecture/tradeoff notes and runnable documentation; no secrets or captured content enter Git/logs.
5. The change is small, committed and reported with one next task. Avoid broader repeated testing unless a change/failure justifies it.

## Requirement coverage

| Requirement group | Planned acceptance location |
| --- | --- |
| All six platforms, native capture boundaries, adaptive accessible UI, clipboard restrictions | 0A–0D, 1A, 1C–1D, 4A |
| Instant/quick/detailed capture; original mixed input; links, notes, Markdown and code; durability and retry | 1B–1E; performance gate |
| Inbox, collections/tags, favorites/read-later/archive/trash, search/filter/sort and duplicate suggestions | 1B–1E, 3C |
| Synchronized self-hosted accounts, ownership, conflicts, outbox/cursors/tombstones and recovery | 2A–2D, 4A |
| Reader/snapshots/security, exports/imports, later integrations/content and P2 work | 1E, 3A–3D, 4A–4D |

The desktop app now has a standalone quick-capture launch mode in 1C.

**Next task:** add the wide Inbox item detail pane in 1C. Sidebar navigation, theme coverage, and web quick-add also remain in 1C.
