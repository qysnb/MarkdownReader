# Markdown Reader Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a touch-friendly reading progress rail, themed adaptive icon, Android Markdown file association, and persisted settings without changing the existing native rendering path.

**Architecture:** Keep `NestedScrollView + TextView + Markwon`; wrap the reader in a Compose `Box` with a 48dp touch target and a narrow progress visual. Extend the serialized reader store with settings and derive the visible recent list in the home UI. Handle external `ACTION_VIEW` intents in the activity and route them through the existing ViewModel.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android `Intent`/SAF, Markwon, kotlinx.serialization, Android vector drawables.

---

### Task 1: Persisted settings and recent-file presentation

**Files:**
- Modify: `app/src/main/java/com/codex/markdownreader/data/ReaderModels.kt`
- Modify: `app/src/main/java/com/codex/markdownreader/data/ReaderViewModel.kt`
- Create: `app/src/main/java/com/codex/markdownreader/data/RecentFilePresentation.kt`
- Test: `app/src/test/java/com/codex/markdownreader/data/RecentFilePresentationTest.kt`

- [ ] Add `recentLimit`, `showRecentPaths`, and `defaultTextScale` to the serializable store and UI state with defaults `10`, `true`, and `1.0f`.
- [ ] Add ViewModel setters that clamp recent limit to `1..50`, clamp default scale to `0.85f..1.5f`, and persist through the existing debounced writer.
- [ ] Add pure helpers to limit recent files and choose a readable path hint with a filename fallback.
- [ ] Test the clamp, ten-item default, and path fallback behavior.

### Task 2: Reading progress rail

**Files:**
- Create: `app/src/main/java/com/codex/markdownreader/ui/ReadingProgressRail.kt`
- Modify: `app/src/main/java/com/codex/markdownreader/MainActivity.kt`

- [ ] Create a Compose rail that observes `NestedScrollView` scroll range through a callback and exposes `onSeek(fraction)`.
- [ ] Give the rail a 48dp minimum touch width while drawing a 5dp track/thumb; map drag and tap to `scrollTo(0, fraction * scrollRange)`.
- [ ] Keep the rail hidden when the document cannot scroll and show a percentage bubble while dragging.
- [ ] Place it in a `Box` over the existing reader without changing the reader's content view or scroll listener persistence.

### Task 3: Settings screen

**Files:**
- Modify: `app/src/main/java/com/codex/markdownreader/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] Add a bottom settings row on the home screen and a settings dialog/screen.
- [ ] Add controls for recent limit, default text scale, and path visibility, plus version and `开发者：Qyforest`.
- [ ] Use `uiState.recentDocuments.take(uiState.recentLimit)` and the path helper for recent rows.
- [ ] Reuse the existing text scale range and keep current document typography behavior.

### Task 4: External Markdown opening

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/codex/markdownreader/MainActivity.kt`

- [ ] Register `ACTION_VIEW` handlers for `text/markdown`, `text/plain`, and Markdown filename extensions.
- [ ] Route `intent.data` through a single activity method for both cold start and `onNewIntent`.
- [ ] Preserve read permission flags and ignore invalid or absent URIs.

### Task 5: Theme icon and verification

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] Add a dark document-page adaptive icon with a high-contrast mathematical symbol.
- [ ] Point application icon and round icon to the new resources.
- [ ] Run unit tests and `assembleDebug`, then manually verify install, progress seeking, settings persistence, and opening an `.md` file from a sharing/open-with flow.
