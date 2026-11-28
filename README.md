# Stash

Stash is an offline-first personal inbox. This first slice saves text or a standalone HTTP(S) link locally, preserving the input exactly. Each save commits the item and a pending synchronization record in one database transaction. Identical deliberate saves are kept; retrying the same capture ID is idempotent. There is no network access or metadata fetch.

The application uses a fixed `local` database partition, not an account. Synchronization, external sharing, web storage and the rest of the product specification are not implemented yet. Room with bundled SQLite stores data in the app's local storage; on desktop it is `~/.stash/stash.db`. Deleting application data deletes the local inbox. The iOS simulator app is not a signed device release.

The checked-in launchers pin Kotlin Toolchain 0.14.0. First use may download dependencies. Android needs an Android SDK; iOS needs macOS, Xcode and a simulator. From the project root:

```sh
./kotlin run -m desktopApp
./kotlin run -m androidApp
./kotlin run -m iosApp -p iosSimulatorArm64
```

On Windows use `kotlin.bat` for supported targets. Run the available checks with:

```sh
./kotlin test -p jvm
./kotlin test -p iosSimulatorArm64
./kotlin build -p android
./kotlin build -p iosSimulatorArm64
```

See [PRODUCT_SPEC.md](PRODUCT_SPEC.md) for the intended product and [docs/architecture.md](docs/architecture.md) for architectural decisions and limitations.

Inbox search runs entirely offline over original text, including URLs. It uses case-insensitive literal substring matching; `%` and `_` have no special meaning. It does not normalize accents or rank results. The current implementation loads the local inbox and filters it in Kotlin, which is simple but will need a scalable index for large inboxes.

In narrow windows, quick capture and Inbox share one scrolling view. Wider windows show them side by side. Use **Save to Inbox** or press **Ctrl+Enter** (**Command+Enter** on macOS) in the capture editor. Enter on its own can add a line to a note.
