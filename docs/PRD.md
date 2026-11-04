# PRD: Android PDF Sheet Music Reader

> **Instruction to the implementing agent:** Build **Phase 1 only**, then **stop and wait for review**. Phases 2 and 3 are included as forward context so Phase 1 design decisions do not paint the project into a corner. Do not implement anything listed under Non-goals.

---

## 1. Product summary

An open-source Android app for reading sheet music from PDF files. It is fast, light, and built for musicians: playlists (setlists), and a reader driven by tap gestures so the user can turn pages without hunting for buttons.

| Item         | Decision                                                                                   |
|--------------|--------------------------------------------------------------------------------------------|
| App name     | Pizzinotta                                                                                 |
| Package ID   | `com.github.dbarbosapn.pizzinotta` (permanent once on Play)                                |
| Platform     | Android only, native Kotlin                                                                |
| Min SDK      | 26 (Android 8.0)                                                                           |
| Form factors | Phones and tablets; tablets (music stand use) are the primary use case                     |
| Input format | PDF only                                                                                   |
| Distribution | Google Play                                                                                |
| Price        | Free, no ads                                                                               |
| Source       | Open-source, Apache-2.0                                                                    |
| Languages    | English only; all strings externalised in `strings.xml` so translations can be contributed |

---

## 2. Global constraints

### 2.1 Performance budgets (Phase 1)
- APK size: **best effort**, target under **10 MB** via ABI splits / app bundle. Not a hard gate — overall runtime performance is the hard requirement.
- Cold start: under **1.5 s** on a 2 GB RAM, API 26 device
- Page turn (tap to new page visible): under **150 ms**
- Adjacent pages (next and previous) are pre-rendered in the background
- Bitmap cache hard-capped at about **3 pages**
- Must stay smooth with PDFs of **100 pages / 50 MB**, including scanned images
- No background services in any phase

### 2.2 Technical constraints
- Native Kotlin. Jetpack Compose is acceptable for library, playlist and settings screens. The **reader** should be a custom `View` (or `SurfaceView`) so rendering and touch handling are controlled directly.
- PDF rendering engine must be **permissively licensed** (Apache-2.0, BSD, MIT) and **able to render arbitrary page regions at arbitrary scale** (needed for sharp zoom). Android's built-in `PdfRenderer` alone is not sufficient for this.
- **No AGPL or GPL dependencies.** (This explicitly excludes MuPDF.)
- **Recommended library: `io.legere:pdfiumandroid` (PdfiumAndroidKt)**, Apache-2.0, a Kotlin-first PDFium binding with a coroutine (suspend) API and a plain non-suspend API. It is a *low-level binding* (document, page, bitmap rendering with arbitrary region and scale), which suits Pizzinotta's custom reader `View`. Use it from Maven Central, and pin the latest stable version at build time (1.0.32 was current when this PRD was written; its declared minimum is API 24, which is compatible with this app's API 26 minimum).
- **Do not use** `barteksc/AndroidPdfViewer` or its many forks. The upstream is abandoned and the forks are fragmented, and a ready-made viewer widget would conflict with the custom gesture, zoom and queue behaviour specified in section 3.4.
- **16 KB page size (best effort):** Google Play requires native libraries to support 16 KB memory pages for apps targeting recent Android versions. This is treated as a **pre-publish checklist item, not a Phase 1 build gate** — the app builds and runs fine regardless of alignment. We aim for a 16 KB-aligned PDFium binary; if publishing on a recent target SDK becomes a goal, verify alignment (`.so` ELF check / APK Analyzer) and align or swap to a permissively-licensed alternative before release. The licence constraint above still applies.
- **APK size:** PDFium ships native libraries per ABI. Use ABI splits or an Android App Bundle so users only download their own ABI. Keeping the APK small is best effort, not a hard requirement (see section 2.1).
- Reader architecture must allow a different page *layout* to be added later without rewriting page rendering. Layout and rendering are separate concerns.
- **Playlist, queue and navigation-boundary logic must be plain Kotlin classes with no Android dependencies**, so unit tests can be added cheaply in Phase 4.
- **Zero runtime permissions** in Phase 1.
- Phases 1 and 2 must build and run with **no Firebase configuration** (see Phase 3).

### 2.3 Data-model sketch (entities and keys, not a schema mandate)
- **Song**: `id` (UUID, stable local identity), `contentHash` (SHA-256 of the file), `displayTitle` (editable; defaults to filename), `originalFilename`, `pageCount`, `addedAt`, `lastOpenedAt`, `filePath` (app-private storage). Phase 2+ will add: Drive file ID, "no longer on Drive" flag. Phase 3 will add: sync state, cloud path.
- **Playlist**: `id` (UUID), `name`, `createdAt`, `updatedAt`.
- **PlaylistEntry**: `playlistId`, `songId`, `position`. A song appears at most once per playlist; a song can be in many playlists.
- **Annotation (Phase 2)**: `id`, `songId`, `pageIndex`, `type`, geometry in **normalised page coordinates (0 to 1)**, colour, thickness, `createdAt`. Always stored relative to the original page, never to screen pixels or zoom, so it is valid at any zoom or screen size.
- All entities must use **UUIDs and timestamps** so Phase 3 sync (union merge, tombstones, last-write-wins) can be added without a migration of identity.

### 2.4 Backup
- Android Auto Backup **includes the database** (playlists, titles, last pages) and **excludes the PDFs** (size).

---

## 3. Phase 1: MVP

### 3.1 Import

**Stories**
- *As a user, I import one or many PDFs from my device so they appear in my library.*
- *As a user, I can open a PDF from another app (file manager, email, Drive app) into this app via Share or "Open with".*

**Behaviour**
- Import is **copy-only** into **app-private internal storage**. The app does not index or read from external folders in place.
- Entry points: an in-app multi-select file picker; Android share intent; "Open with" for `application/pdf`. All three use the same pipeline.
- On import, compute the SHA-256 hash, page count, and UUID.
- Each PDF is copied to app-private storage as `<uuid>.pdf`. The `contentHash` is stored in the database and checked **before** writing, so an identical file is never duplicated on disk (dedup-before-write). Identity (`uuid`) is independent of content (`hash`).

**Acceptance criteria**
- Given the user selects 5 valid PDFs in the picker, when import completes, then all 5 appear in the library.
- Given a PDF whose content hash already exists in the library, when it is imported, then it is not added again and a toast says it is already in the library.
- Given a corrupt, zero-page, or password-protected PDF, when it is imported, then it is rejected at import with a clear error message and nothing is stored.
- Given the app is installed and used in Phase 1, then it has requested no runtime permissions.

### 3.2 Library (the "global menu")

**Behaviour**
- A flat list of all songs. No folders, tags, composer or other metadata.
- Display name defaults to the filename and is **renameable** (display title only; original filename is kept).
- **Search** by title. **Sort** by name or recently opened.
- **Long-press** a song to enter multi-select mode; the action bar offers "Add to playlist" (create new or pick existing) and "Delete".
- **Delete** shows a confirm dialog and removes the song from every playlist.

**Acceptance criteria**
- Given the library has 200 songs, when the user types in search, then the list filters by title substring, case-insensitively, without perceptible lag.
- Given the user long-presses a song and selects 3 more, when they choose "Add to playlist" and pick a playlist, then the 4 songs are appended to it in library display order, and any song already in that playlist is skipped (no duplicates).
- Given a song is in two playlists, when the user deletes it and confirms, then it is removed from the library, from both playlists, and its file is removed from storage.

### 3.3 Playlists

**Behaviour**
- A **Playlists** button on the main screen opens the list of playlists.
- Selecting a playlist opens a **detail view** listing its songs in order, with a **Play** button (starts the first song) and tap-a-song (starts at that song). Both enter the reader with that playlist as the active **queue**.
- Playlists are **ordered**; songs can be **reordered by dragging** and **removed by swiping**.
- Rename and delete playlist are supported.
- A **missing file** (song deleted from disk or library) is **silently removed** from the playlist.
- Opening a song from the **library** (not a playlist) uses the **currently sorted and filtered library list** as the implicit queue.

**Acceptance criteria**
- Given a playlist [A, B, C], when the user drags C above A, then the order is [C, A, B] and persists across app restarts.
- Given a playlist containing song B whose file no longer exists, when the playlist is opened, then B is not shown and the playlist is [A, C].
- Given the library is filtered to 3 songs by search and the user opens the second one, then the queue is exactly those 3 songs in their current order.

### 3.4 Reader

#### 3.4.1 Layout
- **One page at a time**, **fit to screen** (whole page visible, centred, letterboxed if aspect ratios differ).
- **Portrait and landscape** supported; orientation follows the device; no lock toggle. **No two-page spread.**
- **Immersive mode** (system bars hidden). **Screen stays awake** while the reader is open.
- The reader always starts at the **first page** of the selected song. The last-viewed page is **not** remembered or restored: leaving and re-opening a song starts you over at page 1, and you turn forward from there. Zoom is not remembered.

#### 3.4.2 Zoom
- Pinch to zoom about the **pinch focal point**. Minimum zoom = fit; **maximum = 4x**; no zooming out below fit.
- When zoomed, one-finger drag **pans** (standard touch slop distinguishes taps from drags).
- Zoom **resets to fit** whenever the page or song changes.

#### 3.4.3 Tap zones
Zones are measured against the **screen width**, full height:

| Zone       | Width |
|------------|-------|
| Left edge  | 25%   |
| Center     | 50%   |
| Right edge | 25%   |

Gesture detection uses the **OS-default single-tap and double-tap timing**. There is no custom latency or custom double-tap window.

| Gesture    | Zone       | Action                     |
|------------|------------|----------------------------|
| Single tap | Center     | Toggle the bottom bar      |
| Double tap | Center     | Fit the page to the screen |
| Single tap | Right edge | Next page                  |
| Single tap | Left edge  | Previous page              |
| Double tap | Right edge | Next song in the queue     |
| Double tap | Left edge  | Previous song in the queue |

#### 3.4.4 Boundaries
- Single tap on the right edge on the **last page** of a song: **nothing happens**.
- Single tap on the left edge on the **first page** of a song: **nothing happens**.
- Next song / previous song always opens at **page 1**.
- Double tap right edge on the **last song** of the queue: toast **"Double-tap again to start over."** A second double tap within the toast's lifetime opens the first song of the queue. This applies to both playlist queues and the implicit library queue.
- Double tap left edge on the **first song**: symmetric toast **"Double-tap again to go to the last song."**

#### 3.4.5 Bottom bar
Hidden by default. Contains: back button, song title, "page X / Y", queue position (for example "3 / 12") when applicable, previous-song and next-song buttons.

#### 3.4.6 Reader acceptance criteria
- Given the reader is on page 3 of 10 at fit zoom, when the user single-taps the right edge, then page 4 is shown at fit zoom.
- Given the reader is zoomed to 3x on page 3, when the user single-taps the right edge, then page 4 is shown at fit zoom.
- Given the reader is on page 10 of 10, when the user single-taps the right edge, then nothing changes and no message is shown.
- Given the user is on page 5 of song B in queue [A, B, C], when they double-tap the right edge, then song C opens at page 1.
- Given the user is on the last song of the queue, when they double-tap the right edge, then a toast says "Double-tap again to start over", and when they double-tap the right edge again while the toast is visible, then the first song of the queue opens at page 1.
- Given the bar is hidden, when the user single-taps the center, then the bar appears; and when they single-tap the center again, it hides.
- Given the page is zoomed and panned, when the user double-taps the center, then the page returns to fit.
- Given the user pinches in, then the content under the pinch center stays under the fingers, up to the 4x maximum.
- Given the user drags with one finger while zoomed, then the view pans and no tap action fires.
- Given the user turns pages in a 100-page, 50 MB scanned PDF on the reference low-end profile, then page turns meet the 150 ms budget.

### 3.5 Other Phase 1 requirements
- The app UI follows the **system light/dark theme**.
- There is **no settings screen** in Phase 1.
- No analytics, no crash-reporting SDK, no network access.
- Automated unit tests for pure-Kotlin logic (domain records, repositories via fakes) are written **as we develop each component**, not deferred to Phase 4. Logic must still be structured as in section 2.2 so it stays testable. Instrumented tests for reader tap/gesture behaviour remain Phase 4 (see Phase 4).

---

## 4. Phase 2 (forward context; do not build in Phase 1)

### 4.1 Annotations
- Tools: **pen, highlighter, eraser, text note**. Fixed palette of about 6 colours; 3 thicknesses.
- **Undo/redo** per page.
- Input: **finger and stylus**, with palm rejection when a stylus is detected.
- A dedicated **edit mode** disables the tap zones; the bottom bar swaps to a tool palette.
- Annotations are **per song** (not per playlist), stored in the app database in **normalised page coordinates**. The PDF file is **never modified**.

### 4.2 Google Drive sync (on demand)
- **Pull-only** from **one user-picked Drive folder**, **non-recursive**, triggered manually.
- Use Google's folder/file picker and the narrowest viable OAuth scope. Avoid broad restricted scopes.
- Files are matched by **Drive file ID plus content hash**.
- If a file changes on Drive: re-download it, **keep the same song UUID** so annotations survive.
- If a file is deleted from Drive: **keep it locally** and flag it "no longer on Drive". Never silently destroy the user's annotated music.
- Annotations and playlists remain **device-local** in Phase 2.

---

## 5. Phase 3 (forward context; do not build in Phase 1)

### 5.1 Accounts and backend
- **Optional** account. The app remains fully usable offline without one.
- **Google sign-in only.** Backend: **Firebase** (Auth, Firestore, Cloud Storage, App Check, Crashlytics).

### 5.2 What syncs
**Everything**: annotations, playlists, titles, last-read pages, and the **PDF files**.
- **Drive** (Phase 2) is only an **import source**. Once an account exists, Firebase is the source of truth for cross-device data. A Drive-imported file is treated like any other imported file.
- Storage is **per user**, keyed by **song UUID**, with the hash kept as metadata. **No cross-user dedup.**
- Trigger: **on app open** plus a manual **"Sync now"** button. No background services.
- New devices show **cloud-only placeholders** that download on demand. A "download all" action exists, and a playlist's songs are **prefetched** when the playlist is opened.
- **Wi-Fi only** by default, with a toggle to allow mobile data.
- Uploads are **resumable** with retries. Each song shows a **sync status icon** (synced, pending, failed).

### 5.3 Conflict and deletion rules
- Annotations merge as a **union of strokes**, with **tombstones** for deletions.
- Playlists and titles use **last-write-wins** by timestamp.
- **Deleting a song** on any device deletes it **everywhere** (cloud file, annotations, playlist entries). When signed in, the delete confirm dialog warns that it will delete it on all devices.
- If another device holds **newer unsynced annotations** for a deleted song, the **annotations win**: the song and annotations are restored and the user is notified.
- A re-imported edited file has a different hash and is a **new song**.
- **Sign-out** keeps local data. **Account deletion** wipes cloud data and leaves local data intact.

### 5.4 Quotas and cost
- Per-user cap defaults to **500 MB**, with a **50 MB** maximum file size. Both are **config values**, not hard-coded constants. They are enforced in the client and in Storage security rules.
- Settings shows "X MB of 500 MB used".
- A Firebase **budget alert** must be configured on the project.

### 5.5 Open-source repository policy
- `google-services.json` is **gitignored**. The README documents how a contributor creates their own Firebase project.
- **Firestore and Storage security rules and indexes are versioned** in the repo.
- A **no-Firebase build** (flavour or Gradle switch) works fully offline for Phases 1 and 2.
- **Firebase App Check (Play Integrity)** is enabled on Firestore and Storage before Phase 3 launch.

### 5.6 Privacy and policy
- Public **privacy policy**, linked in the Play listing and in the app.
- **Crashlytics** is included **only from Phase 3**, **opt-in, default off**, asked once after sign-in, with a toggle in settings.
- **In-app account deletion** (Firestore, Storage, auth record) and a web link to request it.
- **Terms of use**: files are private to the user, no sharing features exist, **minimum age 16**.
- No data-export feature in the first version of Phase 3.

---

## 6. Phase 4: Automated tests
- Unit tests for the playlist, queue and boundary logic.
- Instrumented tests for the tap and double-tap zone behaviour.
- A documented performance check on an **API 26, 2 GB** emulator profile against the budgets in section 2.1.

---

## 7. Non-goals and unscheduled backlog

These were explicitly considered and rejected. **Do not add them.**

- Auto-crop of margins
- Auto-scroll, continuous vertical scroll mode
- Rendering resolution or quality setting
- Inverted or dark page rendering
- Annotation stamps (fingerings, dynamics, and so on)
- Flattened-PDF export of annotations
- BPM or tempo-based features
- iOS or any non-Android platform
- Password-protected PDFs
- User-to-user sharing, public links, or any social features
- Paid tier or subscriptions (the quota is configurable so one could be added later)
- "Replace file, keep annotations" option
- User data export
- Folder hierarchy, tags, composer or other metadata
- Orientation lock toggle
- Two-page spread
- Analytics of any kind
- Custom double-tap timing