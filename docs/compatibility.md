# Compatibility and CLI evidence

## Phase 0A probe — 2026-11-26

### Retry after tool setup

**iOS Share Extension simulator feasibility (2026-11-27):** A disposable native containing app and embedded Share Extension built and installed with ad hoc simulator signing and an App Group entitlement. On an iPhone 17 Pro simulator (iOS 26.5), a separate host app's `ShareUITests.testShareFromHost` opened the system share sheet and selected the extension while the containing app was not running. The extension saved mixed text and a URL into the shared container; after a separate containing-app launch, it read back the exact bytes. The UI test and build completed with exit 0. Simulator signing worked without a configured development team.

The extension used Foundation's atomic file write and completed the extension request only on success. This is a simulator handoff proof, **not** a durability proof: no explicit file/directory flush, interrupted commit, concurrent handoff, retry, unique IDs, replay, account isolation, URL-only share, success/undo or latency test was performed. Physical-device signing and entitlements are unverified. The first UI test failed because it searched for a button; the extension was exposed as an accessibility cell, and the corrected test passed. The probe was exploratory, not a recorded red/green TDD cycle. The disposable project is `/private/tmp/stash-ios-share-probe`; build used `xcodebuild -project Probe.xcodeproj -target StashProbe -sdk iphonesimulator -configuration Debug SYMROOT=/private/tmp/stash-ios-share-out CODE_SIGN_IDENTITY='-' build -quiet`, followed by simulator install and `xcodebuild -project Probe.xcodeproj -scheme HostProbe -destination 'platform=iOS Simulator,id=EA0B67F8-27A9-4813-92C6-FD5CBDFFC8A5' -derivedDataPath /private/tmp/stash-ios-share-derived CODE_SIGN_IDENTITY='-' -resultBundlePath /private/tmp/stash-ios-share-result4.xcresult test -quiet`. The containing-app readback was verified after a separate `simctl launch`.

**Candidate Room web persistence: resolved probe (2026-11-27).** The previous page freeze was isolated to synchronous `RoomDatabase.close()` immediately following database work: removing that call allowed write/read and browser-restart checks to pass. With Kotlin Toolchain 0.14.0, Kotlin 2.4.20, Room 3.0.3, sqlite-web 2.7.1, SQLite Wasm 3.50.1 and the AndroidX 2.7.1 release-source worker (only its import path adapted for static assets), a stronger Chrome 158.0.8127.88 probe passed in **three independent fresh profiles**. In each profile it waited for an active Room Flow's first emission, concurrently committed two item/outbox transactions, verified both pairs, deliberately threw in another transaction and verified both associated rows rolled back, stopped and joined the Flow, closed the database, restarted Chrome, and verified the pairs and rollback again. All six write/read phases passed. App synchronization used explicit coroutine completion signals, not sleeps. Browser startup used readiness polling.

The tested shutdown stops new calls and waits for existing callers, cancels and joins observers, then cancels and joins a dedicated parent `SupervisorJob` supplied via `setQueryCoroutineContext`, and finally calls `db.close()`. It completed without a scheduling delay in these probes. AndroidX's current source constructs Room's internal `SupervisorJob` with the supplied parent; its synchronous [close barrier](https://android.googlesource.com/platform/frameworks/support/+/7bbade5b385ef86084f186d9ec7874cd27316545/room3/room3-runtime/src/commonMain/kotlin/androidx/room3/concurrent/CloseBarrier.kt) can spin while asynchronous work is pending. The proposed sequence depends on implementation details rather than a documented shutdown guarantee, and must not be treated as production-proven. Do not replace it with an arbitrary delay or a single `yield()`.

The disposable project was `/private/tmp/stash-toolchain-web-probe`. It was built with `KOTLIN_CLI_BOOTSTRAP_CACHE_DIR=/private/tmp/stash-kotlin-cli KOTLIN_CLI_NO_WELCOME_BANNER=1 ./kotlin build -m webApp`; the generated bundle and manually copied worker/runtime assets were served locally with COOP/COEP headers, then `python3 /private/tmp/stash-toolchain-web-probe/repeat-probe.py` ran the browser checks. A fresh read-before-write failed as expected; this control is not a complete TDD red/green cycle. This result validates the candidate feasibility path on Chrome only. It does not validate Safari/Firefox, cross-tab use, quota, migrations, termination mid-transaction, production packaging or physical-device behavior. The Toolchain's direct npm limitation and reproducible asset provisioning remain open; version-pinned source/asset licensing and integrity must be addressed before adoption.

**Version-matched worker retry (2026-11-27):** Retested the candidate stack using the [AndroidX SQLite 2.7.1 release-source worker](https://android.googlesource.com/platform/frameworks/support/+/7bbade5b385ef86084f186d9ec7874cd27316545/sqlite/sqlite-web-worker-test/web-worker/worker.js), with its npm import changed to a static local asset and SQLite Wasm 3.50.1. Chrome 158 still stalled. Diagnostic traces show the worker handled open, schema, transaction begin, insert, transaction end, and a subsequent select step/close, but the page never reported success or responded to CDP. The earlier description that the operation stalled before the first write was too strong: insert and transaction-end requests reached the worker, but their durable outcome and the read result were not verified. The cause remains unproven. No browser restart or persistence pass was obtained; temporary processes were stopped.

The release worker corrects handling of database/statement ID 0 compared with the older sample, but using it did not resolve this stall. Investigate the boundary between worker responses, Room and the probe before choosing the web stack. This diagnostic was exploratory, not a recorded red/green TDD cycle.

**Candidate Toolchain web-worker probe (2026-11-27):** In `/private/tmp/stash-toolchain-web-probe`, Kotlin Toolchain 0.14.0, Kotlin 2.4.20, Room 3.0.3 and `sqlite-web` 2.7.1 built the Wasm application with `KOTLIN_CLI_BOOTSTRAP_CACHE_DIR=/private/tmp/stash-kotlin-cli KOTLIN_CLI_NO_WELCOME_BANNER=1 ./kotlin build -m webApp`. To work around unsupported direct npm integration, the probe manually served SQLite worker and Wasm runtime files as static assets with COOP/COEP headers. Headless Chrome 158 loaded the app, worker, SQLite Wasm and OPFS proxy, but the first database write never completed; the renderer used roughly one CPU core and stopped responding to CDP. The attempt was stopped after approximately 90 seconds and the temporary processes were cleaned up.

The worker came from the older SQLite 2.7.0-alpha04 demo and the runtime was `@sqlite.org/sqlite-wasm` 3.50.1. This mixed-version, manually packaged setup leaves the cause unproven. The result is a successful bundle build and a failed browser probe, not a persistence or restart pass, nor proof that Toolchain or Room is defective. Obtain a version-matched worker and reproducible packaging, then test write, rollback, restart, quota and browser compatibility before selecting web storage or the build system.

**Standalone web storage probe:** A disposable JavaScript IndexedDB experiment ran on Chrome 158.0.8127.88 on macOS arm64 against localhost. One transaction committed an item and outbox record; an explicitly aborted transaction left neither. After terminating and restarting Chrome with the same isolated profile, both committed records remained and both aborted records were absent. The transaction requested the browser's `strict` durability hint; this does not prove power-loss safety. The probe used `node /private/tmp/stash-web-probe/run.mjs write` followed by a Chrome restart and `node /private/tmp/stash-web-probe/run.mjs read`; both passed outside the sandbox. The temporary browser and server were stopped afterwards.

This experiment did not exercise Kotlin/Wasm, the Stash adapter, Room's web worker, other browsers, multi-tab access, migrations, quota handling or process/power loss during commit. It was exploratory, not a recorded red/green TDD cycle. Compare a working Room/OPFS worker with an IndexedDB adapter before selecting web persistence; neither is validated by this result alone.

**Room web-worker comparison (2026-11-27):** A disposable AndroidX-linked Room demo was modified to open a named database instead of its default in-memory database. With Kotlin 2.3.21, Room 3.0.0-alpha04, SQLite 2.7.0-alpha04 and Gradle 8.14.3, `./gradlew :composeApp:wasmJsBrowserDevelopmentWebpack --no-daemon` succeeded after correcting probe-specific interop and webpack configuration errors. A headless Chrome 158 probe served the bundle on localhost with COOP `same-origin` and COEP `require-corp`, wrote a record through Room and its SQLite/OPFS worker, closed the database and restarted Chrome with the same isolated profile. Reading the record afterwards passed. The browser and server were stopped.

The earlier standalone IndexedDB experiment established atomic commit/abort and restart, but did not exercise a Kotlin adapter. This Room experiment exercised Kotlin/Wasm and an OPFS worker, but did not test item/outbox atomicity or rollback. Neither establishes crash/power-loss durability, concurrent tabs, migrations, quota handling or Safari behavior. The Room sample uses older alpha dependencies and Gradle, so it does not validate our candidate Kotlin Toolchain 0.14.0 and Room 3.0.3 combination. The [Kotlin Toolchain Wasm documentation](https://kotlin-toolchain.org/latest/user-guide/product-types/wasm-js-app/) lists direct npm dependencies and Wasm tests as unsupported; the reference worker uses an npm package. Investigate supported worker packaging and browser testing before choosing the build system or web storage. This was exploratory rather than a recorded red/green TDD cycle.

**Room/KSP probe:** In the disposable `/private/tmp/stash-room-probe`, Kotlin Toolchain 0.14.0 with Kotlin 2.4.20, Room 3.0.3 and bundled SQLite 2.7.1 generated Room constructors and per-target schemas for JVM, Android, Wasm, iOS device and iOS simulator. Scoped `./kotlin build -m desktopApp`, `androidApp`, `webApp` and `iosApp` all passed; the iOS build was unsigned. `./kotlin test -m shared -p jvm` and `./kotlin test -m shared -p iosSimulatorArm64` each passed 4 tests, including a real on-disk insert, close, reopen and read. Commands used `KOTLIN_CLI_BOOTSTRAP_CACHE_DIR=/private/tmp/stash-kotlin-cli KOTLIN_CLI_NO_WELCOME_BANNER=1` and approved access outside the sandbox.

The first dependency resolution failed because bundled SQLite was placed in common dependencies while Wasm was enabled. Moving that driver into Android, JVM and iOS dependencies resolved the build. The web target compiled Room code but did not configure a web worker or test persistence in a browser. Android device persistence, migration, atomicity and crash recovery were not tested. The iOS linker emitted cache warnings and reused probe artifacts caused Xcode stale-file warnings. The persistence code preceded its test, so this was a feasibility experiment, not a recorded red/green TDD cycle. These results are not evidence that Stash itself builds or stores data.

**iOS follow-up:** From `/private/tmp/stash-kt-probe`, `KOTLIN_CLI_BOOTSTRAP_CACHE_DIR=/private/tmp/stash-kotlin-cli KOTLIN_CLI_NO_WELCOME_BANNER=1 ./kotlin build -m iosApp` completed successfully outside the sandbox after approximately 90 seconds. It produced Debug-iphoneos and Debug-iphonesimulator app bundles. The toolchain disabled signing (`CODE_SIGNING_ALLOWED=NO`) because no `DEVELOPMENT_TEAM` was configured. `xcrun devicectl list devices` showed no physical iPhone. This verifies the generated unsigned skeleton build only: device installation, signing, simulator execution, Share Extension and Stash features remain untested. This successful follow-up supersedes the interrupted iOS attempts below.

**Simulator follow-up:** Booted an iPhone 17 Pro simulator running iOS 26.5, installed the generated Debug simulator app with `simctl`, and launched `org.example.stashprobe`. The generated Compose starter screen appeared. From the probe directory, `KOTLIN_CLI_BOOTSTRAP_CACHE_DIR=/private/tmp/stash-kotlin-cli KOTLIN_CLI_NO_WELCOME_BANNER=1 ./kotlin test -m shared -p iosSimulatorArm64` passed all 3 generated tests. The linker emitted cache `could not open` warnings despite the successful test result. This supersedes the earlier simulator-execution limitation; physical iPhone signing/install and Stash capture/Share Extension remain untested.

The Android CLI skill is now installed and was consulted. `android --no-metrics -V` reports **1.1.16919734**; `android --no-metrics info` identifies the existing Android SDK, and `android --no-metrics emulator list` lists `Pixel_10`. These commands required access outside the sandbox to the CLI cache lock. `android --no-metrics sdk list` succeeded; its listing showed `cmdline-tools/latest` with an unknown installed version. `xcodebuild -checkFirstLaunchStatus` now exits 0.

In `/private/tmp/stash-kt-probe`, the project's `./kotlin --version` and installed launcher reported **0.14.0**. With `KOTLIN_CLI_BOOTSTRAP_CACHE_DIR=/private/tmp/stash-kotlin-cli` and `KOTLIN_CLI_NO_WELCOME_BANNER=1`, the following scoped commands completed successfully:

| Command | Result |
| --- | --- |
| `./kotlin build -m desktopApp` | Generated desktop application build passed. |
| `./kotlin build -m androidApp` | Generated Android application build passed. No device or instrumentation tests were run. |
| `./kotlin build -m webApp` | Generated Wasm web application build passed. It was not tested in a browser or offline. |
| `./kotlin test -m shared -p jvm` | Three generated smoke tests passed. |

The combined `./kotlin build` reported compilation of shared and iOS code for `iosArm64` and `iosSimulatorArm64`, but it was interrupted after more than two minutes without completing. A scoped `./kotlin build -m iosApp` was interrupted after 30 seconds without output; neither is an iOS build pass. A Compose compiler KLIB loader warning appeared during the combined build. These results only cover the disposable generated project; Room/KSP, iOS extension and signing, native integrations, browser behavior, packaging and server tests remain unverified. Toolchain provisioning accessed normal user caches. **0A remains open and the build-system choice remains provisional.**

Status: **in progress**. The results below supersede the planning-time CLI observations where they differ. No Stash application code or dependency tuple has been built.

| Probe | Observed result |
| --- | --- |
| Kotlin Toolchain launcher | With `KOTLIN_CLI_BOOTSTRAP_CACHE_DIR=/private/tmp/stash-kotlin-cli`, `kotlin --version` bootstrapped successfully outside the sandbox: **0.14.0** (95a1580, 2026-11-16). Network and cache access were required. |
| Four-target generation | `kotlin new /private/tmp/stash-kt-probe --project-id=org.example.stashprobe --target-platform=desktop --target-platform=android --target-platform=ios --target-platform=web --agent=codex --no-git` succeeded in a disposable directory. |
| Four-target build | `kotlin build` failed because Xcode first-launch setup is incomplete. The command also reported missing Android command-line tools and attempted provisioning. No target from this invocation is claimed as passed. |
| Desktop build | From the disposable project, `kotlin build -m desktopApp` passed and compiled the generated shared and desktop JVM targets. |
| Shared JVM tests | From that project, `kotlin test -m shared -p jvm` passed **3 generated smoke tests, 0 failures**. These are not Stash behavior or persistence tests. |
| Gradle baseline | Cached Gradle 9.4.1 ran an empty offline Java build with a temporary Gradle user home outside the sandbox. Its compilation and tests had no source; this proves startup only. |
| Android CLI | Modern `android` and `sdkmanager` were not found; `adb version` reported 37.0.1. |

The Toolchain commands used `KOTLIN_CLI_NO_WELCOME_BANNER=1` and the temporary bootstrap cache above. The Toolchain also provisioned Gradle, Temurin JDK 25 and test dependencies in its normal user cache. Sandbox execution initially failed on network DNS and Gradle file-lock socket access; the successful checks used approved elevated execution.

**Decision remains open:** this is a useful desktop smoke result, not enough evidence to choose the Alpha Toolchain over Gradle. Room/KSP, persistent storage tests, Android instrumentation, iOS extension/App Groups, web worker bundling, desktop native integrations, packaging, CI and server tests remain unverified. The Xcode setup needs `sudo xcodebuild -runFirstLaunch` outside this investigation; no privileged setup was performed. Complete the remaining 0A probes before locking the build system.

Checked 2026-11-26. **Documentation compatibility is partially established; application build compatibility is unverified.** The repository has no source, build definitions, wrapper or tests. No project builds/tests were run, no skeleton was generated and no tools/skills were installed in this planning task.

## Local environment: observed results

| Check actually executed | Result | Consequence |
| --- | --- | --- |
| `uname -ms`, `sw_vers` | Darwin arm64; macOS 27.2.1 | This host cannot establish Windows/Linux native integration or packaging support. |
| `java -version`, `/usr/libexec/java_home -V` | Default Oracle JDK 22.0.2; registered JDK 16.0.2 also present | Neither is a pinned project toolchain. Proposed Gradle runtime JDK 21 needs provisioning if selected. |
| Android Studio bundled `java -version` | OpenJDK 25.0.4 | Present, but not evidence that project Gradle/plugins support this runtime. |
| `command -v gradle kotlinc`, cache file inventory | Neither on PATH; cached Gradle 9.1.0/9.4.1 and Kotlin/Native distributions exist | Caches do not replace a project wrapper or prove compatibility. |
| `xcode-select -p`, `xcodebuild -version`, `xcodebuild -showsdks`, `swift --version` | Xcode 27.2; iOS/simulator/macOS 27.2 SDKs; Swift 6.4.2. SDK inventory emitted cache/file-event warnings. | Apple SDKs are present; compiler compatibility, signing, entitlements and device execution remain untested. |
| Android SDK directory inventory | Android Studio and SDK installed; platforms 29–37.0, build-tools through 36.0.0; license files exist | File presence does not prove a successful build, accepted terms for every package or a functioning device. |
| `command -v sdkmanager avdmanager`; direct path checks | Absent; `cmdline-tools/latest` contains only installer metadata | Repair modern CLI/package management in 0A; do not assume the empty directory is an installed tool. |
| `adb version` | ADB 1.0.41, platform-tools 37.0.1 | Executable works; no device connection, install or instrumentation test was attempted. |
| Absolute-path `emulator -version` | Failed: incompatible processor; required feature `neon` | Emulator is not validated on this host. Diagnose binary/host compatibility; use a physical device where needed. Do not mask this failure with a passing file-presence check. |
| `node --version` | Node 25.8.0 | Browser build/test runtime is not pinned. Use the selected build tool's supported Node version. |
| `docker --version`, `docker info --format '{{.ServerVersion}}'` | Docker CLI 29.3.1; daemon connection failed because socket is absent | PostgreSQL/container integration tests cannot currently run through Docker. |

No SDK installation/update, emulator launch, device mutation, network service startup or signing change was performed.

## Kotlin Toolchain CLI: can we use it?

**Yes, as a candidate for phase 0; adoption is conditional.** JetBrains documents a unified CLI for KMP creation/build/test/run and declarative `project.yaml`/`module.yaml` configuration. It is Alpha. It is a different build workflow from running standalone `kotlinc`, and it is not just a Gradle alias. See [Kotlin's Toolchain guide](https://kotlinlang.org/docs/multiplatform/kotlin-toolchain.html) and the [Toolchain CLI reference](https://kotlin-toolchain.org/latest/cli/).

Local inspection found `/Users/artembondar/.local/bin/kotlin`. Its launcher pins **Toolchain 0.14.0** and a distribution checksum. Both `kotlin --version` and `kotlin --help` failed before launching: creating `~/Library/Caches/JetBrains` was denied by the sandbox. That cache directory is absent. This proves the launcher exists, not that the runtime is downloaded or usable. No permission was requested merely to finish planning.

The launcher supports `KOTLIN_CLI_BOOTSTRAP_CACHE_DIR`; a future feasibility run can use an authorized writable cache, or approved normal cache access. First run downloads the runtime. Inspect/record that behavior and pin the project wrapper; do not automatically update the user's global launcher or shell settings. Start with version/help; do not run project creation inside this repository during planning.

Toolchain has documented [Room/KSP configuration](https://kotlin-toolchain.org/latest/user-guide/builtin-tech/ksp/room/), including per-platform schema output to avoid concurrent writes. That is evidence of integration support, not proof of our complete stack. The [FAQ](https://kotlin-toolchain.org/latest/faq/) and tutorial differ in describing web execution: verify against the pinned version rather than assuming web `run` parity.

0A must prove these capabilities before choosing Toolchain:

1. Shared coroutine tests and real database/migration tests on JVM, Android, iOS and browser targets; machine-readable failures usable in CI.
2. Room/KSP generation, schema export and Wasm worker/resource bundling on the selected versions.
3. Android share activity/instrumentation and native iOS Share Extension, App Group entitlements and extension-safe linking.
4. Desktop native shortcut integration and installable distributions on macOS/Windows/Linux.
5. Reproducible wrapper/provisioning, server tests and the small amount of custom build work needed for web offline assets.

If a required capability lacks a maintainable supported route, use Gradle. Keep only one authoritative build configuration. The architecture does not depend on either build system.

## Android CLI and skills: can we use them?

**Yes.** Google's modern Android CLI provides SDK/device/emulator operations, documentation lookup, testing/profiling and skills integration. It complements KMP/Gradle or Toolchain; it does not establish cross-platform build support. See the [official overview](https://developer.android.com/tools/agents/android-cli).

`command -v android` returned no executable; `~/.local/bin/android` is absent. A legacy `sdk/tools/android` file exists but is not proof of the modern CLI. Google's SDK-manager and AVD-manager pages now direct users to modern [SDK commands](https://developer.android.com/tools/sdkmanager) and [emulator commands](https://developer.android.com/tools/avdmanager).

During 0A, install a verified macOS arm64 release only when tool setup is in scope, record the version and use `android --no-metrics` for task commands. Inspect help before scripting. Check `android info`, `android sdk list` and device/emulator availability against the existing SDK; avoid unrequested global updates. Keep `adb` for explicit device operations. Do not use an Android-only generator to replace the KMP layout.

Skill findings:

| Skill/source | Verified status | Planned use |
| --- | --- | --- |
| Local `i-have-adhd` | Found and read; active for communication | Next action first, small numbered steps, visible completion, one next task. |
| Official [Android CLI skill](https://github.com/android/skills/blob/main/devtools/android-cli/SKILL.md) and [android/skills](https://github.com/android/skills) | Upstream skill inspected; not installed in searched local skill roots | Use for CLI/device/docs work once setup is requested. Official docs provide `android init`, `android skills list` and selected skill installation. Review generated changes to agent instructions before accepting them. |
| Official [Kotlin agent skills](https://github.com/Kotlin/kotlin-agent-skills) | Repository verified; no matching local Kotlin/Android skills found | Select relevant skills for concrete work, such as AGP 9 migration or native build performance; inspect before installing. |
| Dedicated Kotlin Toolchain skill | Kotlin's guide mentions one, but the inspected official `skills` listing did not include it; search found a contribution/PR, not verified merged availability | Recheck upstream in 0A. Do not claim a dedicated official skill is installed or adopt an unverified third-party equivalent. Toolchain documentation is sufficient to plan the probe. |

Only availability/relevance was evaluated. No plugin/skill installer, `android init` or generator was run.

## Technology compatibility: documented support and required proof

| Technology | Current official evidence | Recommendation / build gate |
| --- | --- | --- |
| KMP and Compose | Android/iOS/JVM desktop stable; Wasm/web Beta. [Platform stability](https://kotlinlang.org/docs/multiplatform/supported-platforms.html) | Suitable candidate for shared logic/main UI. Test browser accessibility, cold startup and native capture independently. |
| Kotlin/Gradle/AGP/Xcode | Kotlin 2.4.21 table lists Gradle 7.6.3–9.7.0, AGP 8.5.2–9.3.1, Xcode 26.4. [KMP compatibility](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html) | Installed Xcode 27.2 is outside that documented pairing. A matching SDK/toolchain or a successful explicitly recorded compatibility probe is needed. Do not suppress warnings and call it supported. |
| Gradle candidate | [AGP 9.3 notes](https://developer.android.com/build/releases/agp-9-3-0-release-notes) require Gradle 9.5.0, JDK >=17, build-tools 36.0.0. [Gradle JVM matrix](https://docs.gradle.org/current/userguide/compatibility.html) governs runtime choice. | Proposed experiment: Kotlin 2.4.21, Compose 1.12.2, AGP 9.3.1, Gradle 9.5.0, JDK 21. This is a candidate intersection, not a tested dependency lock. |
| Compose compiler/OS reach | [Compose compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) lists 1.12.2 and requires compiler plugin version equal to Kotlin. Current table lists macOS 13 arm64, Windows 10 x64/arm64, Ubuntu 20.04 x64/arm64 and WasmGC browsers. | Confirm required hardware/OS reach; Intel Mac support must not be assumed. Combined library minimums may be higher. Agree/document minimums before release. |
| Android KMP plugin | Google documents `com.android.kotlin.multiplatform.library`, AGP >=8.10. [Plugin setup](https://developer.android.com/kotlin/multiplatform/plugin) | Gradle route uses a separate Android application module and KMP library plugin. Verify resources, tests and KSP; avoid obsolete scaffold patterns. Toolchain owns its own integration settings. |
| Room/SQLite | [Room 3](https://developer.android.com/jetpack/androidx/releases/room3) lists stable 3.0.3 and JS/Wasm support; its worker integration is not turnkey. [Room 2](https://developer.android.com/jetpack/androidx/releases/room) stable 2.8.5 remains another candidate. | Prefer a stable release; current [KMP setup example](https://developer.android.com/kotlin/multiplatform/room) uses 3.1 alpha, so do not copy versions blindly. Probe Room 3.0.3/SQLite 2.7.1 and a separate IndexedDB adapter. |
| KSP | [KSP KMP guide](https://kotlinlang.org/docs/ksp-multiplatform.html) demonstrates Kotlin 2.4.21/KSP 2.3.12 with Room 3.0.3 | Gradle route configures processors per target; catch-all `ksp(...)` is invalid in current KMP. Build and compare generated schemas. |
| SQLDelight alternative | [SQLDelight 2.1 JS driver](https://sqldelight.github.io/sqldelight/2.1.0/js_sqlite/) documents worker-backed browser SQL | Does not by itself prove persistent storage or Kotlin/Wasm wiring. Adopt only if the same persistence probes show less maintenance than Room/IndexedDB. |
| Coroutines/serialization | [Coroutine tests](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/) expose Native/JS/Wasm targets; [serialization JSON](https://kotlinlang.org/docs/serialization.html) is multiplatform/stable | Compile/run selected runtime versions on every target. Serialization runtime versions are independent of Kotlin; verify plugin/runtime together. |
| Ktor Client/server | [Client engines](https://ktor.io/docs/client-engines.html) document JVM/Android/Native/JS/Wasm (docs show 3.6.2) | Candidate 3.6.2; prove selected engines on real targets, cancellation/TLS, browser cookies/CORS and test transport. Keep server dependencies JVM-only. |
| Koin/navigation | [Koin targets](https://insert-koin.io/docs/reference/koin-core/kmp-setup/) include JS/Wasm. [Compose Navigation](https://kotlinlang.org/docs/multiplatform/compose-navigation.html) documents shared routes; current example uses an RC. | Constructor injection initially; add Koin only where wiring benefits. Select tested stable Navigation artifacts when routes are needed; verify deep links/history/back/focus. Neither belongs in the capture critical path. |
| PostgreSQL/Exposed/Flyway | [Exposed JDBC](https://www.jetbrains.com/help/exposed/transactions.html) blocks; [Flyway](https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database) needs separate PostgreSQL support and lists version 18 verified; [PostgreSQL FTS](https://www.postgresql.org/docs/current/textsearch.html) is available | Prefer a supported PostgreSQL 18 patch candidate over assuming newly released 19 is verified by Flyway. Pin the server tuple in 2A, then run real migrations, rollback/ownership/concurrency and restore tests. No server compatibility claim yet. |

Version locks must record exact artifacts, transitive/target variants, wrapper checksums and tested host/SDKs. A Toolchain selection must use its documented supported version controls; do not apply Gradle's candidate tuple blindly to its managed defaults.

## Required build evidence before a target is called supported

| Target | Future evidence to record |
| --- | --- |
| JVM desktop | Shared tests and persistent reopen/transaction tests; native capture/packaging on macOS, Windows, Linux and Wayland where applicable. |
| Android | APK build, unit and instrumented tests, share with app stopped, device latency; record SDK/device/API and actual commands. |
| iOS | Simulator build/tests plus physical-device Share Extension/App Group/signing, termination recovery and capture latency. |
| Web | Production Wasm bundle and browser tests in Chromium/Firefox/Safari; offline reload, transaction/reopen/multi-tab/quota, accessibility and clipboard restrictions. |
| Server | JVM tests plus PostgreSQL integration/migration/tenant/sync/restore tests through a working container runtime or documented local equivalent. |

Discover task names from the selected tool's help/task inventory when the skeleton exists. Do not present hypothetical `./gradlew` or `./kotlin` commands as commands already run. An unavailable target stays explicitly unverified.
