# Overlay panel device checklist

Authoring this file does not require an emulator or device. Every row starts as
**unverified** and only becomes verified after someone runs it on a real device and
records the observed result.

Preconditions for every row: Peek overlay service is running, a screen capture session
is available unless the row says otherwise, and the panel is the floating overlay variant.

| # | Step | Expected result | Status |
|---|------|-----------------|--------|
| 1 | Tap the prompt text field once. | Soft keyboard appears. The panel does not move or resize away from the tap. The panel drag handle still moves the panel while the keyboard is open. | unverified |
| 2 | Close the keyboard (back gesture or IME close control), then reopen the panel later. | The previous app regains input focus, the panel is non-focusable again, and the next panel show opens with no keyboard. | unverified |
| 3 | With the keyboard closed, tap outside the panel. | The panel dismisses without opening a keyboard, and the underlying app keeps the tap. | unverified |
| 4 | Focus the prompt field, leave the keyboard open, then start a screen-region capture. | The capture hides the panel and keyboard. After the capture finishes, the panel returns with the same window flags, and the keyboard does not reappear by itself. | unverified |
| 5 | Rotate the device while the panel is visible, including with the keyboard open. | The panel is re-clamped fully on screen in the new orientation; no edge is outside the display and no stale keyboard inset remains. | unverified |
| 6 | Enter split-screen mode with Peek open, move the panel to one half, then resize or swap the split. | The panel stays in its chosen half of the display and remains fully visible; it does not jump to the other half. | unverified |
| 7 | Kill the projection from the system screen-sharing control (or stop the media projection service), then tap a capture action. | The overlay itself keeps working. The next capture prompts for fresh screen-capture consent instead of silently failing with a stale session. | unverified |
