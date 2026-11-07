package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.res.Configuration
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Display
import android.view.KeyEvent
import android.widget.FrameLayout
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.util.OverlayGeometry
import com.example.util.PanelBounds
import com.example.MainActivity
import com.example.ui.components.ScreenSelectorView
import com.example.util.BitmapUtils
import com.example.ui.components.OverlayToolboxView
import com.example.ui.screens.ImagePickerActivity
import com.example.R
import com.example.ui.components.FloatingBubbleView
import com.example.ui.components.OverlayPanelView
import com.example.ui.screens.ScreenCaptureActivity
import com.example.ui.screens.VoicePermissionActivity
import com.example.ui.screens.ScreenSelectorActivity
import com.example.ui.theme.PeekTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlin.math.abs

private data class OverlayWindowState(val expanded: Boolean, val toolbox: Boolean, val capturing: Boolean, val bitmap: Bitmap?)

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lifecycleOwner: ServiceLifecycleOwner? = null

    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var toolboxWindow: FrameLayout? = null
    private var toolboxCompose: ComposeView? = null
    private var toolboxParams: WindowManager.LayoutParams? = null

    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelFocusRelease by mutableIntStateOf(0)
    private var speechRecognizer: SpeechRecognizer? = null
    private var voiceListening by mutableStateOf(false)
    private var displayManager: DisplayManager? = null
    private val displayListener = OverlayDisplayListener(
        panelDisplayId = { panelView?.display?.displayId ?: Display.DEFAULT_DISPLAY },
        onPanelDisplayRemoved = { relocatePanelToDefaultDisplay() }
    )

    private var selectorWindow: FrameLayout? = null
    private var selectorCompose: ComposeView? = null
    private var selectorParams: WindowManager.LayoutParams? = null

    private val layoutPrefs by lazy { getSharedPreferences("peek_overlay_layout", MODE_PRIVATE) }
    private var preferredPanel: PanelBounds? = null
    private var keyboardInset = 0

    private val channelId = "peek_overlay_channel"
    private val notificationId = 1001

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        active = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        displayManager = getSystemService(DisplayManager::class.java)
        displayManager?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        createNotificationChannel()

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(notificationId, notification, fgsType)
        } else {
            startForeground(notificationId, notification)
        }

        lifecycleOwner = ServiceLifecycleOwner().apply { onCreate() }

        if (Settings.canDrawOverlays(this)) {
            setupViews()
            if (OverlayStateManager.isServiceRunning.value) observeState()
        } else {
            OverlayStateManager.setErrorMessage("Display over other apps permission is required.")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CAPTURE) {
            val delayMs = intent.getLongExtra("capture_delay", 0L)
            scope.launch { delay(delayMs); requestScreenCapture() }
        }
        if (intent?.action == ACTION_VOICE) startVoiceInput()
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupViews() {
        val owner = lifecycleOwner ?: return

        // 1. Setup Floating Bubble
        val bubbleBounds = OverlayWindowMetrics.bounds(this)
        bubbleParams = OverlayWindowParams.bubble(
            (layoutPrefs.getFloat("bubble_x", 0.05f) * bubbleBounds.width()).toInt(),
            (layoutPrefs.getFloat("bubble_y", 0.35f) * bubbleBounds.height()).toInt()
        )

        bubbleView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)

            setContent {
                val opacity by OverlayPreferences.getInstance(this@OverlayService).buttonOpacity.collectAsState()
                PeekTheme {
                    FloatingBubbleView(
                        opacity = opacity,
                        onClick = { OverlayStateManager.toggleToolbox() },
                        onLongClick = { OverlayStateManager.setPanelExpanded(true) },
                        onDrag = { dx, dy -> OverlayStateManager.setToolboxExpanded(false); moveBubble(dx, dy) },
                        onDragEnd = { snapBubble() }
                    )
                }
            }


        }

        runCatching { windowManager.addView(bubbleView, bubbleParams) }.onFailure {
            OverlayStateManager.setErrorMessage("Could not show Peek overlay: ${it.localizedMessage}")
            stopSelf()
            return
        }
        OverlayStateManager.setServiceRunning(true)

        // 2. Setup Overlay Panel - bound strictly to the panel's dimensions so taps outside reach underlying app
        val displayMetrics = resources.displayMetrics
        val panelScreen = OverlayWindowMetrics.bounds(this)
        val migrateToolkit = !layoutPrefs.getBoolean("toolkit_layout_v2", false)
        val widthDp = layoutPrefs.getFloat("panel_width_dp", 320f).let { if (migrateToolkit) it.coerceAtMost(320f) else it }
        val heightDp = layoutPrefs.getFloat("panel_height_dp", 440f).let { if (migrateToolkit) it.coerceAtMost(440f) else it }
        val panelWidth = (widthDp * displayMetrics.density).toInt()
        val panelHeight = (heightDp * displayMetrics.density).toInt()
        preferredPanel = OverlayGeometry.clampPanel(PanelBounds(
            (layoutPrefs.getFloat("panel_x", ((panelScreen.width() - panelWidth) / panelScreen.width().toFloat()).coerceAtLeast(0f)) * panelScreen.width()).toInt(),
            (layoutPrefs.getFloat("panel_y", 0.10f) * panelScreen.height()).toInt(),
            panelWidth, panelHeight
        ), panelScreen.width(), panelScreen.height(), displayMetrics.density)

        if (migrateToolkit) {
            savePanelBounds()
            layoutPrefs.edit().putBoolean("toolkit_layout_v2", true).apply()
        }
        panelParams = OverlayWindowParams.panel(preferredPanel!!)

        panelView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)

            // When user taps outside the visible overlay card, release input focus and
            // minimize the panel without blocking touch to the app.
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    setPanelFocus(false)
                    OverlayStateManager.setPanelExpanded(false)
                    true
                } else {
                    false
                }
            }

            setContent {
                PeekTheme {
                    OverlayPanelView(
                        modifier = Modifier.fillMaxWidth(),
                        onMove = { dx, dy -> changePanel(dx, dy, resize = false) },
                        onResize = { dx, dy -> changePanel(dx, dy, resize = true) },
                        onGeometryChanged = { savePanelBounds() },
                        onCropImageRequested = { cropImage() },
                        onCaptureScreenRequested = { requestScreenCapture() },
                        onPickImageRequested = { openImagePicker() },
                        onMinimizeRequested = {
                            setPanelFocus(false)
                            OverlayStateManager.setPanelExpanded(false)
                        },
                        onCloseRequested = { stopSelf() },
                        onOpenSettingsRequested = { openSettings() },
                        onInputRequested = { setPanelFocus(true) },
                        inputFocusRelease = panelFocusRelease,
                        isVoiceListening = voiceListening,
                        onVoiceRequested = { startVoiceInput() },
                        onVoiceCancelRequested = { stopVoiceInput() }
                    )
                }
            }
        }
        toolboxCompose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                PeekTheme {
                    OverlayToolboxView(
                        onSelect = { requestScreenCapture() },
                        onAddImage = { openImagePicker() },
                        onChat = { OverlayStateManager.setPanelExpanded(true) },
                        onStop = { stopSelf() }
                    )
                }
            }
        }
        toolboxWindow = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) OverlayStateManager.setToolboxExpanded(false)
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            isFocusableInTouchMode = true
            addView(toolboxCompose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    // Leave button taps to its toggle handler, rather than dismissing
                    // just before that handler runs and accidentally reopening the menu.
                    val location = IntArray(2)
                    bubbleView?.getLocationOnScreen(location)
                    val overButton = event.rawX >= location[0] && event.rawX < location[0] + (bubbleView?.width ?: 0) &&
                        event.rawY >= location[1] && event.rawY < location[1] + (bubbleView?.height ?: 0)
                    if (!overButton) OverlayStateManager.setToolboxExpanded(false)
                    true
                } else false
            }
        }
        toolboxParams = OverlayWindowParams.toolbox(
            PanelBounds(0, 0, (88 * displayMetrics.density).toInt(), (240 * displayMetrics.density).toInt())
        )
        selectorCompose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                val bitmap by OverlayStateManager.capturedScreenToCrop.collectAsState()
                PeekTheme {
                    bitmap?.let { screenshot ->
                        ScreenSelectorView(screenshot, onSelectionConfirmed = { rect ->
                            val version = OverlayStateManager.captureVersion
                            scope.launch {
                                try {
                                    val crop = withContext(Dispatchers.Default) { BitmapUtils.crop(screenshot, rect) }
                                    if (OverlayStateManager.isCapturing.value && OverlayStateManager.captureVersion == version) {
                                        OverlayStateManager.completeImageSelection(crop)
                                        OverlayStateManager.finishCapture()
                                    } else if (crop !== screenshot) crop.recycle()
                                } catch (e: Exception) {
                                    if (OverlayStateManager.captureVersion == version) {
                                        OverlayStateManager.setErrorMessage("Could not crop selection: ${e.localizedMessage}")
                                        OverlayStateManager.finishCapture()
                                    }
                                }
                            }
                        }, onCancel = { OverlayStateManager.finishCapture(showPanel = false) })
                    }
                }
            }
        }
        selectorWindow = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) OverlayStateManager.finishCapture(showPanel = false)
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            addView(selectorCompose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        selectorParams = OverlayWindowParams.selector()
        panelView?.let { view ->
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
                val inset = (insets.getInsets(WindowInsetsCompat.Type.ime()).bottom -
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom).coerceAtLeast(0)
                if (keyboardInset != inset) {
                    val keyboardWasOpen = keyboardInset > 0
                    keyboardInset = inset
                    if (keyboardWasOpen && inset == 0) setPanelFocus(false)
                    view.post { updatePanelBounds() }
                }
                insets
            }
        }
        moveBubble(0f, 0f)
    }

    private fun moveBubble(dx: Float, dy: Float) {
        val params = bubbleParams ?: return
        val density = resources.displayMetrics.density
        val bounds = OverlayWindowMetrics.bounds(this)
        val size = (60 * density).toInt()
        val (x, y) = OverlayGeometry.clampBubble(params.x + dx.toInt(), params.y + dy.toInt(), size, bounds.width(), bounds.height())
        params.x = x; params.y = y
        bubbleView?.takeIf { it.isAttachedToWindow }?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        if (OverlayStateManager.isToolboxExpanded.value) positionToolbox()
    }

    private fun snapBubble() {
        val params = bubbleParams ?: return
        val metrics = resources.displayMetrics
        val bounds = OverlayWindowMetrics.bounds(this)
        val size = (60 * metrics.density).toInt()
        params.x = if (params.x + size / 2 < bounds.width() / 2) 0 else bounds.width() - size
        moveBubble(0f, 0f)
        layoutPrefs.edit().putFloat("bubble_x", params.x.toFloat() / bounds.width())
            .putFloat("bubble_y", params.y.toFloat() / bounds.height()).apply()
    }

    private fun changePanel(dx: Float, dy: Float, resize: Boolean) {
        val preferred = preferredPanel ?: return
        val actual = panelParams ?: return
        preferredPanel = if (resize) preferred.copy(width = actual.width + dx.toInt(), height = actual.height + dy.toInt())
            else preferred.copy(x = actual.x + dx.toInt(), y = actual.y + dy.toInt())
        val bounds = OverlayWindowMetrics.bounds(this)
        preferredPanel = OverlayGeometry.clampPanel(preferredPanel!!, bounds.width(), bounds.height(), resources.displayMetrics.density)
        updatePanelBounds()
    }

    private fun updatePanelBounds() {
        val preferred = preferredPanel ?: return
        val params = panelParams ?: return
        val bounds = OverlayWindowMetrics.bounds(this)
        val fit = OverlayGeometry.clampPanel(preferred, bounds.width(), (bounds.height() - keyboardInset).coerceAtLeast(1), resources.displayMetrics.density)
        params.width = fit.width; params.height = fit.height; params.x = fit.x; params.y = fit.y
        panelView?.takeIf { it.isAttachedToWindow }?.let { runCatching { windowManager.updateViewLayout(it, params) } }
    }

    /**
     * Single toggle for the panel's input focus. The window is added non-focusable;
     * only the prompt field may turn focus on, and closing the keyboard, tapping
     * outside, or hiding the panel turns it back off.
     */
    private fun setPanelFocus(focused: Boolean) {
        // Capture owns the screen and the panel is hidden; focus changes here would
        // either pop a keyboard over the captured frame or leave focus on a gone window.
        if (OverlayStateManager.isCapturing.value || !OverlayStateManager.isPanelExpanded.value) return
        val view = panelView ?: return
        val params = panelParams ?: return
        val isFocusable = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0
        if (focused && isFocusable) return

        val bounds = PanelBounds(params.x, params.y, params.width, params.height)
        val preset = if (focused) {
            OverlayWindowParams.focusedPanel(bounds)
        } else {
            OverlayWindowParams.panel(bounds)
        }
        params.flags = preset.flags
        params.softInputMode = preset.softInputMode

        if (focused) {
            if (view.isAttachedToWindow) {
                runCatching { windowManager.updateViewLayout(view, params) }
            }
            view.post {
                view.requestFocus()
                ViewCompat.getWindowInsetsController(view)?.show(WindowInsetsCompat.Type.ime())
            }
        } else {
            panelFocusRelease++
            ViewCompat.getWindowInsetsController(view)?.hide(WindowInsetsCompat.Type.ime())
            view.clearFocus()
            if (isFocusable && view.isAttachedToWindow) {
                runCatching { windowManager.updateViewLayout(view, params) }
            }
        }
    }

    /**
     * Voice follow-ups stay behind the panel's input row, so a non-focusable panel can
     * take a prompt without ever asking for the keyboard.
     */
    private fun startVoiceInput() {
        if (OverlayStateManager.isCapturing.value || !OverlayStateManager.isPanelExpanded.value) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            VoicePermissionActivity.launch(this)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            OverlayStateManager.setErrorMessage(
                "Speech recognition is not available on this device. Use the follow-up suggestions instead."
            )
            return
        }

        stopVoiceInput()
        val recognizer = runCatching { SpeechRecognizer.createSpeechRecognizer(this) }.getOrNull()
        if (recognizer == null) {
            OverlayStateManager.setErrorMessage(
                "Could not start voice input. Use the follow-up suggestions or type a question."
            )
            return
        }
        speechRecognizer = recognizer
        voiceListening = true
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) OverlayStateManager.setDraftPrompt(text)
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!text.isNullOrBlank()) {
                    OverlayStateManager.setDraftPrompt(text)
                } else {
                    OverlayStateManager.setErrorMessage(
                        "No speech detected. Use the follow-up suggestions or type a question."
                    )
                }
                stopVoiceInput()
            }

            override fun onError(error: Int) {
                OverlayStateManager.setErrorMessage(voiceErrorMessage(error))
                stopVoiceInput()
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { recognizer.startListening(intent) }.onFailure {
            OverlayStateManager.setErrorMessage("Could not start voice input: ${it.localizedMessage}")
            stopVoiceInput()
        }
    }

    private fun voiceErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission denied. Use the follow-up suggestions instead."
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "No speech detected. Use the follow-up suggestions or type a question."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition needs a network connection. Use the follow-up suggestions instead."
        else ->
            "Voice input failed. Use the follow-up suggestions or type a question."
    }

    private fun stopVoiceInput() {
        voiceListening = false
        speechRecognizer?.let { recognizer ->
            runCatching { recognizer.stopListening() }
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
        }
        speechRecognizer = null
    }

    private fun relocatePanelToDefaultDisplay() {
        val bounds = OverlayWindowMetrics.bounds(this)
        preferredPanel = preferredPanel?.let {
            DisplayRelocation.rebase(it, bounds.width(), bounds.height(), resources.displayMetrics.density)
        }
        updatePanelBounds()
        // The service's WindowManager targets the default display, so re-adding the same
        // view instance is what actually moves it off the removed display.
        val view = panelView
        val params = panelParams
        if (view != null && params != null && view.isAttachedToWindow) {
            runCatching { windowManager.removeView(view) }
            runCatching { windowManager.addView(view, params) }.onFailure {
                OverlayStateManager.setErrorMessage(
                    "Could not move Peek to the default display: ${it.localizedMessage}"
                )
            }
        }
        moveBubble(0f, 0f)
        if (OverlayStateManager.isToolboxExpanded.value) positionToolbox()
    }

    private fun savePanelBounds() {
        val bounds = preferredPanel ?: return
        val density = resources.displayMetrics.density
        val screen = OverlayWindowMetrics.bounds(this)
        layoutPrefs.edit().putFloat("panel_x", bounds.x.toFloat() / screen.width())
            .putFloat("panel_y", bounds.y.toFloat() / screen.height())
            .putFloat("panel_width_dp", bounds.width / density)
            .putFloat("panel_height_dp", bounds.height / density).apply()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        keyboardInset = 0
        val screen = OverlayWindowMetrics.bounds(this)
        val density = resources.displayMetrics.density
        preferredPanel = preferredPanel?.copy(
            x = (layoutPrefs.getFloat("panel_x", 0.04f) * screen.width()).toInt(),
            y = (layoutPrefs.getFloat("panel_y", 0.10f) * screen.height()).toInt()
        )
        updatePanelBounds()
        if (OverlayStateManager.isToolboxExpanded.value) positionToolbox()
        bubbleParams?.let {
            it.x = (layoutPrefs.getFloat("bubble_x", 0.05f) * screen.width()).toInt()
            it.y = (layoutPrefs.getFloat("bubble_y", 0.35f) * screen.height()).toInt()
        }
        moveBubble(0f, 0f)
    }

    private fun openImagePicker() {
        OverlayStateManager.setToolboxExpanded(false)
        OverlayStateManager.setPanelExpanded(false)
        runCatching { ImagePickerActivity.launch(this) }.onFailure {
            OverlayStateManager.setErrorMessage("Could not open images: ${it.localizedMessage}")
            OverlayStateManager.setPanelExpanded(true)
        }
    }

    private fun openSettings() {
        OverlayStateManager.setToolboxExpanded(false)
        OverlayStateManager.setPanelExpanded(false)
        startActivity(Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_SETTINGS
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
    }

    private fun positionToolbox() {
        val button = bubbleParams ?: return
        val params = toolboxParams ?: return
        val bounds = OverlayWindowMetrics.bounds(this)
        val fit = OverlayGeometry.toolboxBounds(button.x, button.y, bounds.width(), bounds.height(), resources.displayMetrics.density)
        params.x = fit.x; params.y = fit.y; params.width = fit.width; params.height = fit.height
        toolboxWindow?.takeIf { it.isAttachedToWindow }?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun requestScreenCapture() {
        OverlayStateManager.setToolboxExpanded(false)
        if (OverlayStateManager.isCapturing.value) return
        // Release the editor before consent or capture masks/hides the panel.
        setPanelFocus(false)
        if (!OverlayStateManager.isScreenSharing.value) {
            ScreenCaptureActivity.launch(this)
            return
        }
        if (!OverlayStateManager.beginCapture()) return
        if (!MediaProjectionService.requestFrame()) {
            OverlayStateManager.finishCapture()
            OverlayStateManager.setErrorMessage("Screen access ended. Tap Capture to reconnect.")
        }
    }

    private fun cropImage() {
        val bitmap = OverlayStateManager.currentImage.value ?: return
        setPanelFocus(false)
        if (OverlayStateManager.beginCapture(replaceCurrentImage = true)) OverlayStateManager.setCapturedScreenToCrop(bitmap)
    }

    private fun observeState() {
        combine(OverlayStateManager.isPanelExpanded, OverlayStateManager.isToolboxExpanded, OverlayStateManager.isCapturing, OverlayStateManager.capturedScreenToCrop) { expanded, toolbox, capturing, bitmap ->
            OverlayWindowState(expanded, toolbox, capturing, bitmap)
        }.onEach { (expanded, toolbox, capturing, bitmap) ->
            // Keep the window's Compose recomposer attached across captures. Detaching a
            // retained composition cancels its window recomposer and can leave stale UI.
            fun hide(view: View?) { view?.visibility = View.GONE }
            if (capturing) {
                stopVoiceInput()
                hide(bubbleView); hide(panelView); hide(toolboxWindow)
                if (bitmap != null) {
                    selectorWindow?.visibility = View.VISIBLE
                    if (selectorWindow?.isAttachedToWindow == false) runCatching { windowManager.addView(selectorWindow, selectorParams) }.onFailure {
                        OverlayStateManager.setErrorMessage("Could not show region selection: ${it.localizedMessage}")
                        OverlayStateManager.finishCapture()
                    }
                } else if (bitmap == null) hide(selectorWindow)
                return@onEach
            }
            hide(selectorWindow)
            if (toolbox) {
                positionToolbox()
                toolboxWindow?.visibility = View.VISIBLE
                if (toolboxWindow?.isAttachedToWindow == false) {
                    runCatching { windowManager.addView(toolboxWindow, toolboxParams); toolboxWindow?.requestFocus() }.onFailure {
                        OverlayStateManager.setToolboxExpanded(false)
                        OverlayStateManager.setErrorMessage("Could not show toolbox: ${it.localizedMessage}")
                        OverlayStateManager.setPanelExpanded(true)
                    }
                }
            } else hide(toolboxWindow)
            if (!expanded) {
                setPanelFocus(false)
                stopVoiceInput()
            }
            val view = if (expanded) panelView else bubbleView
            hide(if (expanded) bubbleView else panelView)
            if (expanded) updatePanelBounds()
            view?.visibility = View.VISIBLE
            if (view?.isAttachedToWindow == false) {
                // Reuse the stored params instance. Rebuilding here would reset the
                // panel's focus/touch flag word after every capture.
                runCatching { windowManager.addView(view, if (expanded) panelParams else bubbleParams) }.onFailure {
                    OverlayStateManager.setErrorMessage("Could not display Peek. Check overlay permission.")
                    stopSelf()
                }
            }
        }.launchIn(scope)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Peek Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the active floating Peek AI assistant overlay"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Peek AI Overlay Active")
            .setContentText("Tap the floating button to open the toolbox.")
            .setSmallIcon(R.drawable.ic_peek_mark)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Overlay", pendingStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager?.unregisterDisplayListener(displayListener)
        stopVoiceInput()
        if (active === this) active = null
        stopService(Intent(this, MediaProjectionService::class.java))
        OverlayStateManager.setServiceRunning(false)
        OverlayStateManager.setToolboxExpanded(false)
        OverlayStateManager.setPanelExpanded(false)
        OverlayStateManager.cancelRequest()
        OverlayStateManager.cancelImageLoading()
        if (OverlayStateManager.isCapturing.value) {
            OverlayStateManager.finishCapture(showPanel = false)
        }

        scope.cancel()
        if (selectorWindow?.isAttachedToWindow == true) runCatching { windowManager.removeView(selectorWindow) }
        if (toolboxWindow?.isAttachedToWindow == true) runCatching { windowManager.removeView(toolboxWindow) }
        toolboxCompose?.disposeComposition()
        selectorCompose?.disposeComposition()
        panelView?.disposeComposition()
        bubbleView?.disposeComposition()
        lifecycleOwner?.onDestroy()

        runCatching {
            if (bubbleView?.isAttachedToWindow == true) {
                windowManager.removeView(bubbleView)
            }
        }
        runCatching {
            if (panelView?.isAttachedToWindow == true) {
                windowManager.removeView(panelView)
            }
        }
    }

    companion object {
        private var active: OverlayService? = null
        private const val ACTION_CAPTURE = "com.example.overlay.CAPTURE"
        private const val ACTION_VOICE = "com.example.overlay.VOICE"

        fun requestVoiceInput(context: Context) {
            val service = active
            if (service != null) {
                service.startVoiceInput()
            } else {
                val intent = Intent(context, OverlayService::class.java).setAction(ACTION_VOICE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun captureScreen(context: Context, delayMs: Long = 0) {
            val service = active
            if (service != null) {
                service.scope.launch { delay(delayMs); service.requestScreenCapture() }
            } else {
                val intent = Intent(context, OverlayService::class.java).setAction(ACTION_CAPTURE).putExtra("capture_delay", delayMs)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            }
        }

        fun cropCurrentImage(context: Context): Boolean {
            val service = active ?: return false
            service.cropImage()
            return true
        }

        const val ACTION_STOP = "com.example.service.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.stopService(intent)
        }
    }
}
