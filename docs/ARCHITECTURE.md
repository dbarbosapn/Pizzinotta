# Pizzinotta — Architecture

Living architecture record for Pizzinotta, an open-source Android PDF sheet-music reader.
Paired with [`docs/PRD.md`](./PRD.md). Update this doc as decisions are made.

## 1. Product snapshot

- Native Kotlin Android app for reading sheet music from **PDF only**. Fast, light, gesture-driven page turns.
- Package: `com.github.dbarbosapn.pizzinotta` · minSdk **26** · targetSdk **37**.
- Distribution: Google Play, free, no ads. Source: open-source, Apache-2.0.
- Primary form factor: **tablets / music stand**; phones supported.
- Phase 1 = MVP (this record). Phases 2–4 are forward context and are **not** built in Phase 1.

## 2. Hard constraints (Phase 1)

- **Performance**: cold start < 1.5 s (2 GB RAM, API 26); page turn < 150 ms; adjacent pages pre-rendered;
  bitmap cache hard-capped at ~3 pages; smooth on 100-page / 50 MB scanned PDFs; no background services.
- **License**: permissively licensed PDF engine (Apache-2.0 / BSD / MIT). **No AGPL/GPL** (MuPDF excluded).
- **Zero runtime permissions** in Phase 1.
- Builds and runs with **no Firebase configuration** (Phases 1–2).

## 3. Best-effort (non-blocking) constraints

- **APK size**: target < 10 MB via ABI splits / app bundle — best effort, not a hard gate.
- **16 KB page alignment**: aim for an aligned PDFium binary; treated as a pre-publish checklist item, non-blocking for Phase 1.

## 4. Architecture decisions

### 4.1 App architecture: MVVM + Repository

- One `ViewModel` per screen holding repository references; screens expose state via `StateFlow`/`SharedFlow`
  and collect with `collectAsStateWithLifecycle`.
- **Repository abstraction over Room** (`SongRepository`, `PlaylistRepository`). Repositories handle persistence only.
- **Pure-Kotlin domain layer**: playlist ordering, queue construction, tap-boundary logic are plain Kotlin classes
  with **no Android deps**, kept separate from repositories/ViewModel so they stay unit-testable (Phase 4).

### 4.2 Persistence: Room

- SQLite via Room. Entities use **UUIDs + timestamps** for stable identity and future sync (union merge / tombstones / last-write-wins, Phase 3).
- Entities: `Song`, `Playlist`, `PlaylistEntry` (`PlaylistEntry` keyed by `playlistId`+`songId`; a song appears at most once per playlist, in many playlists). Annotations (`Phase 2`) stored normalised to page coords (0–1).

### 4.3 Storage on disk

- Import is **copy-only** into app-private internal storage; nothing indexed from external folders in place.
- Files stored as `<uuid>.pdf`. `contentHash` (SHA-256) kept in the DB as the **dedup key, checked before writing**
  (identical files are never duplicated on disk), and as sync metadata. Identity (`uuid`) is independent of content (`hash`).

### 4.4 Reader

- Custom `View`/`SurfaceView` for rendering + touch (not a ready-made viewer widget).
- **Layout and rendering are separate** so a different page layout can be added later without rewriting rendering.
- PDF engine behind a small interface; the reader never talks to the engine directly.

### 4.5 Non-reader UI: Jetpack Compose

- Library, playlists, settings screens in Compose (Material 3). Follows system light/dark theme. No settings screen in Phase 1.
- Lightweight **Compose Navigation** with a sealed `Screen` type and nav arguments for song/playlist IDs.

### 4.6 PDF engine

- **pdfiumandroid** (`io.legere:pdfiumandroid`, Apache-2.0): Kotlin-first PDFium binding, arbitrary region/scale rendering,
  suspend + plain APIs. Pinned stable version; availability and 16 KB alignment verified before relying on it (see §3).

## 5. Build plan — component by component, reviewed after each

Build in this order and stop after each component for human review/commit.

| # | Component | Contents |
|---|-----------|----------|
| 1 | Data layer | Room entities (`Song`, `Playlist`, `PlaylistEntry`), DAOs, `AppDatabase`, `SongRepository` + `PlaylistRepository` (Room impls) |
| 2 | Import pipeline | SAF picker / share / open-with → hash → pdfium validate → copy `<uuid>.pdf` → persist; async per-file results |
| 3 | Library screen | Flat list, rename, search, sort, multi-select delete, import trigger |
| 4 | Playlists | List → detail, drag-reorder / swipe-remove, rename/delete, missing-file drop, queue to reader |
| 5 | Reader skeleton | pdfium → one page fit in `SurfaceView`; pre-render queue + bitmap cache as pure Kotlin |
| 6 | Full reader gestures | pinch zoom, pan, tap zones, boundaries/toasts, bottom bar, immersive + keep-awake |

## 6. Decisions log

- **2026-11-02** — Build order: import-first, component-by-component; stop after each isolated component for review/commit.
- **2026-11-02** — APK size < 10 MB is best-effort (ABI splits / app bundle), not a hard requirement. Runtime performance stays hard.
- **2026-11-02** — 16 KB page alignment is best-effort / pre-publish checklist item, non-blocking for Phase 1.
- **2026-11-02** — `targetSdk` = 37, `minSdk` = 26. Alignment treated as a pre-publish checklist item.
- **2026-11-02** — Persistence: Room. Entities use UUIDs + timestamps.
- **2026-11-02** — Architecture: MVVM + Repository + StateFlow; pure-Kotlin domain layer kept separate from persistence; lightweight Compose Navigation.
- **2026-11-02** — PDF engine: pdfiumandroid (Apache-2.0). No AGPL/GPL dependencies.
- **2026-11-02** — On-disk storage: files named `<uuid>.pdf`; `contentHash` is the pre-write dedup key + sync metadata in the DB.
- **2026-11-02** — Testing: unit tests written as we develop each component (JVM/JUnit4). Domain records and repositories are tested via in-memory fake DAOs/clients; no emulator needed. Instrumented reader tap/gesture tests remain Phase 4.
