# Overlay panel device checklist

Authoring this file does not require an emulator or device. Every row starts as
**unverified** and only becomes verified after someone runs it on a real device and
records the observed result.

Preconditions for every row: the panel is the floating overlay variant; a screen capture
session is available and the overlay service is running unless the row says otherwise.

| # | Step | Expected result | Status |
|---|------|-----------------|--------|
| 1 | Tap the prompt text field once. | Soft keyboard appears. The panel does not move or resize away from the tap. The panel drag handle still moves the panel while the keyboard is open. | unverified |
| 2 | Close the keyboard (back gesture or IME close control), then reopen the panel later. | The previous app regains input focus, the panel is non-focusable again, and the next panel show opens with no keyboard. | unverified |
| 3 | With the keyboard closed, tap outside the panel. | The panel dismisses without opening a keyboard, and the underlying app keeps the tap. | unverified |
| 4 | Focus the prompt field, leave the keyboard open, then start a screen-region capture. | The capture hides the panel and keyboard. After the capture finishes, the panel returns with the same window flags, and the keyboard does not reappear by itself. | unverified |
| 5 | Rotate the device while the panel is visible, including with the keyboard open. | The panel is re-clamped fully on screen in the new orientation; no edge is outside the display and no stale keyboard inset remains. | unverified |
| 6 | Enter split-screen mode with Peek open, move the panel to one half, then resize or swap the split. | The panel stays in its chosen half of the display and remains fully visible; it does not jump to the other half. | known limitation |
| 7 | Kill the projection from the system screen-sharing control (or stop the media projection service), then tap a capture action. | The overlay itself keeps working. The next capture prompts for fresh screen-capture consent instead of silently failing with a stale session. | unverified |
| 8 | With Peek stopped, deny notifications on Android 13+, tap Start Peek, then re-grant notifications in system settings and tap Start Peek again. | While denied, Peek refuses to start and shows the inline reason that the persistent notification is required. After re-granting, Start Peek starts the overlay without reinstalling or clearing data. | unverified |

Row 6 is a **known limitation**, not a pending verification. `OverlayWindowMetrics` asks
`WindowManager.getCurrentWindowMetrics()` from the service context on API 30+, and on SDK 34
that returns the whole display rather than a split-screen cell (see
`OverlayWindowMetricsTest`). The panel is therefore still free to float over the other app's
half; the row stays here so a future platform change can be re-checked.

Row 4 is covered at the state level by
`OverlayStateManagerTest.typedDraftSurvivesACaptureCycle` and
`UiReworkTest.releasingEditorFocusKeepsTheDraft`: the typed draft was not dropped.
