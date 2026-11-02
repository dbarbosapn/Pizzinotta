# AGENTS — Pizzinotta

Working notes for agents building this repo. Keep this current as the setup evolves.

## Running Gradle WITHOUT hanging the shell

Gradle starts a daemon JVM that **inherits the shell's stdout pipe** and keeps it open after `gradlew`
returns. The tool reads output until EOF on that pipe, so a lingering daemon means EOF never arrives and
the call appears to hang forever.

Always run Gradle like this:
- `--no-daemon` — no persistent daemon to hold the pipe open.

## Unit tests

- Task: `testDebugUnitTest`. Pure-KVM/JUnit4 tests live in `app/src/test`; they need no emulator.
- Room uses KSP (plugin `com.google.devtools.ksp`, version pinned to match Kotlin in `libs.versions.toml`).
- Room column names default to the **exact field name** (camelCase), NOT snake_case — write queries accordingly.

## Project layout / conventions

- Package: `com.github.dbarbosapn.pizzinotta`. Min SDK 26, target SDK 37.
- Native Kotlin + Jetpack Compose for non-reader screens; reader is a custom `View`/`SurfaceView`.
- Architecture: MVVM + Repository over Room. Domain logic (playlist/queue/boundary) stays plain Kotlin with no
  Android deps so it stays unit-testable. See `docs/ARCHITECTURE.md` and `docs/PRD.md`.
- Build iteratively feature-by-feature; stop after each isolated component for review before continuing.
