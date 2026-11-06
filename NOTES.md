# Peek Overlay — Recon Notes & Refactor Scope

Branch: `refactor/overlay-window-hygiene` (from `origin/main`). Working tree was clean before branching.

## Recon summary

**App:** Android (Kotlin, Jetpack Compose, minSdk 24, targetSdk 36). Two services:
- `OverlayService` (`app/src/main/java/com/example/service/OverlayService.kt`) — owns the floating bubble, chat panel, toolbox, and region-selector windows. All four views are `ComposeView`/`FrameLayout` windows created in `setupViews()` with `WindowManager.LayoutParams(...)` literals (construction sites: lines ~150, 210, 303, 355 — the only four in the codebase).
- `MediaProjectionService` (`MediaProjectionService.kt`) — owns the screenshot consent session and `ScreenCaptureHelper` (`ScreenCaptureHelper.kt`), which keeps one `VirtualDisplay` + `ImageReader` per consent session and retains only the newest frame.

**Flags today:**
- Bubble: `FLAG_NOT_FOCUSABLE | FLAG_HARDWARE_ACCELERATED`, `TYPE_APPLICATION_OVERLAY` (O+; `TYPE_PHONE` pre-O), TRANSLUCENT, TOP|START, drag-snapped, persisted as fractions of screen size.
- Panel: `FLAG_NOT_TOUCH_MODAL | FLAG_WATCH_OUTSIDE_TOUCH | FLAG_HARDWARE_ACCELERATED` — i.e. the panel **is focusable** so its `OutlinedTextField` can take keyboard input. Adjacent app keeps touch; `ACTION_OUTSIDE` minimizes the panel without focus changes (`setOnTouchListener` on the ComposeView).
- Toolbox: same as panel + `SOFT_INPUT_STATE_ALWAYS_HIDDEN`, back-key interception via `dispatchKeyEvent`.
- Selector: `MATCH_PARENT`, `FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS | HW_ACCEL` with `setFitInsetsTypes(0)` and cutout ALWAYS on R+ so the frozen screen aligns pixel-for-pixel with the region selection.

**Drag/position logic:** Compose `detectDragGestures` inside `OverlayPanelView`/`FloatingBubbleView` calls back into the service (`onMove`/`onResize` → `changePanel`/`moveBubble`), which clamps via `OverlayGeometry.clampPanel`/`clampBubble`/`toolboxBounds` and applies through `updateViewLayout` on the same params object (views are never re-added to mutate flags). Bounds persist in `peek_overlay_layout` prefs; `onConfigurationChanged` re-reads and reclamps. Keyboard insets handled via `ViewCompat.setOnApplyWindowInsetsListener` + `keyboardInset`.

**Capture path:** `requestScreenCapture()` → `MediaProjectionService.requestFrame()` → `captureRegion()` coroutine → `ScreenCaptureHelper.capture(requestedAt)`. Before reading a frame it `delay(200)` so the just-hidden overlay UI (views hidden via `visibility = GONE` in `observeState()`) is out of the compositor's newest frame — the overlay never OCRs itself. Selection → crop → `OverlayStateManager.completeImageSelection`.

**Projection robustness already in place:** `MediaProjection.Callback` registered (`onStop` closes everything + `onStopped()` → service stops → `isScreenSharing=false` → next capture re-launches consent). Android 14+ single-use consent tokens are handled by design because nothing caches the consent `Intent`: `ScreenCaptureActivity` re-runs the consent flow when `isScreenSharing` is false. Android 14 (`UPSIDE_DOWN_CAKE`)+ already passes `MediaProjectionConfig.createConfigForDefaultDisplay()` to `createScreenCaptureIntent`. Resize handled via `onCapturedContentResize` (reader recreated, `display.resize`).

**Foreground service types:** OverlayService `specialUse|dataSync` (subtype property set), MediaProjectionService `mediaProjection`. Per-API `startForeground` type selection matches the manifest.

**Permissions:** `SYSTEM_ALERT_WINDOW` gated in `onCreate` (`Settings.canDrawOverlays`) with a state error message + `stopSelf` fallback; `POST_NOTIFICATIONS`, `SATE`, picker flows handled in `MainActivity`/ImagePicker (out of scope for this branch unless a gap shows up).

## Scope of this branch (and deliberate exclusions)

Included — window/lifecycle hygiene, capture-pipeline robustness, Compose UI polish:
- **A:** A single `OverlayWindowParams.kt` builder with named presets (bubble/panel/toolbox/selector); no `WindowManager.LayoutParams(...)` literals left in `OverlayService`. Robolectric unit tests assert each preset's type, format, gravity, and flag invariants (bubble non-focusable like every Android chathead, panel touch-modal-open + outside-watch, selector no-limits).
- **B:** `getMediaProjection(resultCode, resultData, config)` on API 34+ so the session matches the consent UI config (default-display only). The rest of B2 was already implemented (verified above) and is only tested/documented here.
- **C:** Follow-up prompt chips (more detail / step by step / shorter) shown after an AI answer, complementing the existing first-screenshot chips (Explain / Read text / Translate).

Deliberately excluded (not in this branch):
- Any behavior whose target is a specific web page's `blur`/`focus` event stream: no `FLAG_NOT_FOCUSABLE`-everywhere posture, no focus-drop/restore "mode" machinery, no quiz-log replica fixture, no flags-event log. The panel keeps focusability because users type in it; the bubble stays non-focusable, which is the standard chathead posture for windows that never need input.
- The capture-path hide/grab/show sequence stays as-is (`visibility=GONE` + settle delay). It already keeps the overlay out of its own frames; a remove/add dance would add churn (recreating windows mid-flight) with no behavioral gain here.

## Known quirks / limitations
- Instrumented (on-device) tests can't run in this environment (no emulator attached); manager-level invariants are covered by Robolectric unit tests instead.
- The panel focus behavior is Robolectric-pinned (idle and focused `WindowManager.LayoutParams` presets) but not yet device-verified; the real tap-to-focus and keyboard-close transitions still need an on-device check.
- `ScreenCaptureHelper.capture()`'s 200ms settle delay after hiding is empirical (compositor latency), not a frame-count guarantee.
- `OverlayStateManager`'s `scope` is a global object scope — acceptable for a singleton owned by app lifetime, noted for future teardown work.

## Verification log (this environment)
- `WindowManager.LayoutParams` construction in the codebase now happens only inside `OverlayWindowParams.kt` (`grep "WindowManager.LayoutParams("` → 6 matches, all inside the builder object + test file).
- Local Gradle caveats, unrelated to this change: `local.properties` had to be written (`sdk.dir=C:/Users/SUHARTO/AppData/Local/Android/Sdk`); Gradle/AGP fails under the machine's JDK 26/JDK 25 with a `JdkImageTransform` jlink error, so builds here run with `JAVA_HOME` pointed at Eclipse Temurin JDK 21; `google-services.json` is absent so the Google Services plugin runs in its WARN strategy (build still succeeds).
- `:app:testDebugUnitTest` → BUILD SUCCESSFUL, including the new `OverlayWindowParamsTest` (4 tests: bubble flag/format/type/gravity, panel flag/soft-input invariants, toolbox invariants, selector limits/cutout invariants).

## API-verified correction
- `MediaProjectionManager` has **no** `getMediaProjection(resultCode, resultData, config)` overload (checked framework docs, API through 37): the `MediaProjectionConfig` path is only `createScreenCaptureIntent(config)`, which `ScreenCaptureActivity` already calls with `createConfigForDefaultDisplay()` on API 34+. An intermediate commit that assumed otherwise was reverted; the consent single-use invariant is enforced structurally (nothing caches `resultData`; stop → service death → fresh consent on next capture).

## Commit map
- `a71abdd` docs: recon notes & scope (this file)
- `5c5d054` refactor: `OverlayWindowParams` builder + `OverlayService` migration + `OverlayWindowParamsTest`
- `07d96aa` + `b438367` projection-consent comment correction (supersedes an incorrect first take)
- `bfe04d8` feat: follow-up prompt chips ("Explain that in more detail" / "Walk me through it step by step" / "Summarize that in a few bullets") shown after the last non-error AI answer in the floating panel
