package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.res.Configuration
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.KeyEvent
import android.widget.FrameLayout
import android.view.Gravity
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
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
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val metrics = resources.displayMetrics
            x = (layoutPrefs.getFloat("bubble_x", 0.05f) * metrics.widthPixels).toInt()
            y = (layoutPrefs.getFloat("bubble_y", 0.35f) * metrics.heightPixels).toInt()
        }

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
        val migrateToolkit = !layoutPrefs.getBoolean("toolkit_layout_v2", false)
        val widthDp = layoutPrefs.getFloat("panel_width_dp", 320f).let { if (migrateToolkit) it.coerceAtMost(320f) else it }
        val heightDp = layoutPrefs.getFloat("panel_height_dp", 440f).let { if (migrateToolkit) it.coerceAtMost(440f) else it }
        val panelWidth = (widthDp * displayMetrics.density).toInt()
        val panelHeight = (heightDp * displayMetrics.density).toInt()
        preferredPanel = OverlayGeometry.clampPanel(PanelBounds(
            (layoutPrefs.getFloat("panel_x", ((displayMetrics.widthPixels - panelWidth) / displayMetrics.widthPixels.toFloat()).coerceAtLeast(0f)) * displayMetrics.widthPixels).toInt(),
            (layoutPrefs.getFloat("panel_y", 0.10f) * displayMetrics.heightPixels).toInt(),
            panelWidth, panelHeight
        ), displayMetrics.widthPixels, displayMetrics.heightPixels, displayMetrics.density)

        if (migrateToolkit) {
            savePanelBounds()
            layoutPrefs.edit().putBoolean("toolkit_layout_v2", true).apply()
        }
        panelParams = WindowManager.LayoutParams(
            preferredPanel!!.width,
            preferredPanel!!.height,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferredPanel!!.x
            y = preferredPanel!!.y
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        panelView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)

            // When user taps outside the visible overlay card, minimize the panel without blocking touch to the app
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
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
                            OverlayStateManager.setPanelExpanded(false)
                        },
                        onCloseRequested = { stopSelf() },
                        onOpenSettingsRequested = { openSettings() }
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
        toolboxParams = WindowManager.LayoutParams(
            (88 * displayMetrics.density).toInt(), (240 * displayMetrics.density).toInt(), layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        }
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
        selectorParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Projection includes system bars. Use the identical display bounds so the
            // frozen screen and its selection do not shrink or shift under the overlay.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        panelView?.let { view ->
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
                val inset = (insets.getInsets(WindowInsetsCompat.Type.ime()).bottom -
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom).coerceAtLeast(0)
                if (keyboardInset != inset) {
                    keyboardInset = inset
                    view.post { updatePanelBounds() }
                }
                insets
            }
        }
        moveBubble(0f, 0f)
    }

    private fun moveBubble(dx: Float, dy: Float) {
        val params = bubbleParams ?: return
        val metrics = resources.displayMetrics
        val size = (60 * metrics.density).toInt()
        val (x, y) = OverlayGeometry.clampBubble(params.x + dx.toInt(), params.y + dy.toInt(), size, metrics.widthPixels, metrics.heightPixels)
        params.x = x; params.y = y
        bubbleView?.takeIf { it.isAttachedToWindow }?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        if (OverlayStateManager.isToolboxExpanded.value) positionToolbox()
    }

    private fun snapBubble() {
        val params = bubbleParams ?: return
        val metrics = resources.displayMetrics
        val size = (60 * metrics.density).toInt()
        params.x = if (params.x + size / 2 < metrics.widthPixels / 2) 0 else metrics.widthPixels - size
        moveBubble(0f, 0f)
        layoutPrefs.edit().putFloat("bubble_x", params.x.toFloat() / metrics.widthPixels)
            .putFloat("bubble_y", params.y.toFloat() / metrics.heightPixels).apply()
    }

    private fun changePanel(dx: Float, dy: Float, resize: Boolean) {
        val preferred = preferredPanel ?: return
        val actual = panelParams ?: return
        preferredPanel = if (resize) preferred.copy(width = actual.width + dx.toInt(), height = actual.height + dy.toInt())
            else preferred.copy(x = actual.x + dx.toInt(), y = actual.y + dy.toInt())
        val metrics = resources.displayMetrics
        preferredPanel = OverlayGeometry.clampPanel(preferredPanel!!, metrics.widthPixels, metrics.heightPixels, metrics.density)
        updatePanelBounds()
    }

    private fun updatePanelBounds() {
        val preferred = preferredPanel ?: return
        val params = panelParams ?: return
        val metrics = resources.displayMetrics
        val fit = OverlayGeometry.clampPanel(preferred, metrics.widthPixels, (metrics.heightPixels - keyboardInset).coerceAtLeast(1), metrics.density)
        params.width = fit.width; params.height = fit.height; params.x = fit.x; params.y = fit.y
        panelView?.takeIf { it.isAttachedToWindow }?.let { runCatching { windowManager.updateViewLayout(it, params) } }
    }

    private fun savePanelBounds() {
        val bounds = preferredPanel ?: return
        val metrics = resources.displayMetrics
        layoutPrefs.edit().putFloat("panel_x", bounds.x.toFloat() / metrics.widthPixels)
            .putFloat("panel_y", bounds.y.toFloat() / metrics.heightPixels)
            .putFloat("panel_width_dp", bounds.width / metrics.density)
            .putFloat("panel_height_dp", bounds.height / metrics.density).apply()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        keyboardInset = 0
        val metrics = resources.displayMetrics
        preferredPanel = preferredPanel?.copy(
            x = (layoutPrefs.getFloat("panel_x", 0.04f) * metrics.widthPixels).toInt(),
            y = (layoutPrefs.getFloat("panel_y", 0.10f) * metrics.heightPixels).toInt()
        )
        updatePanelBounds()
        if (OverlayStateManager.isToolboxExpanded.value) positionToolbox()
        bubbleParams?.let {
            it.x = (layoutPrefs.getFloat("bubble_x", 0.05f) * metrics.widthPixels).toInt()
            it.y = (layoutPrefs.getFloat("bubble_y", 0.35f) * metrics.heightPixels).toInt()
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
        val metrics = resources.displayMetrics
        val fit = OverlayGeometry.toolboxBounds(button.x, button.y, metrics.widthPixels, metrics.heightPixels, metrics.density)
        params.x = fit.x; params.y = fit.y; params.width = fit.width; params.height = fit.height
        toolboxWindow?.takeIf { it.isAttachedToWindow }?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun requestScreenCapture() {
        OverlayStateManager.setToolboxExpanded(false)
        if (OverlayStateManager.isCapturing.value) return
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
            val view = if (expanded) panelView else bubbleView
            hide(if (expanded) bubbleView else panelView)
            if (expanded) updatePanelBounds()
            view?.visibility = View.VISIBLE
            if (view?.isAttachedToWindow == false) {
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
