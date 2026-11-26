# Development instructions
Read PRODUCT_SPEC.md before changing code. Treat capture speed, data durability and simplicity as core requirements.
- Prefer idiomatic Kotlin, readable names, small cohesive functions and explicit ownership of coroutine scopes. Avoid magic numbers, unexplained delays, blocking UI threads, unnecessary abstractions and speculative frameworks.
- Use structured concurrency, deterministic tests and injectable clocks/dispatchers where needed. Do not use arbitrary sleeps for synchronization or tests.
- Keep platform-specific code behind narrow interfaces. Do not force shared UI or storage where native constraints make it unreliable.
- Validate dependency compatibility with actual builds. Never claim a target builds or tests pass without running it; report unavailable SDKs and tooling.
- Implement small vertical slices, with tests and documentation. Keep secrets out of git; provide example configuration.
- Document architectural decisions and tradeoffs. Ask before irreversible or scope-changing decisions. Do not silently omit requirements.
- Review changes for correctness, privacy, accessibility, maintainability, error handling and offline behavior. Prefer human-readable code over cleverness.
