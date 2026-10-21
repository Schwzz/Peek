package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.ui.components.FloatingBubbleView
import com.example.ui.components.OverlayPanelView
import com.example.ui.screens.ScreenCaptureActivity
import com.example.ui.theme.PeekTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lifecycleOwner: ServiceLifecycleOwner? = null

    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private val channelId = "peek_overlay_channel"
    private val notificationId = 1001

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
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
            observeState()
        } else {
            OverlayStateManager.setErrorMessage("Display over other apps permission is required.")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        bubbleView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)

            setContent {
                PeekTheme {
                    FloatingBubbleView(
                        onClick = {
                            OverlayStateManager.togglePanel()
                        }
                    )
                }
            }

            // Draggable Touch Listener with reliable tap detection
            val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isDragging = false
            var startClickTime = 0L

            setOnTouchListener { view, event ->
                val params = bubbleParams ?: return@setOnTouchListener false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        startClickTime = System.currentTimeMillis()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                            isDragging = true
                        }
                        if (isDragging) {
                            params.x = initialX + dx
                            params.y = initialY + dy
                            runCatching { windowManager.updateViewLayout(view, params) }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val clickDuration = System.currentTimeMillis() - startClickTime
                        if (!isDragging && clickDuration < 400) {
                            view.performClick()
                            OverlayStateManager.togglePanel()
                        } else if (isDragging) {
                            // Snap to closest screen edge
                            val metrics = resources.displayMetrics
                            val screenWidth = metrics.widthPixels
                            params.x = if (params.x + view.width / 2 < screenWidth / 2) 20 else screenWidth - view.width - 20
                            runCatching { windowManager.updateViewLayout(view, params) }
                        }
                        true
                    }
                    else -> false
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
        val panelWidth = (displayMetrics.widthPixels * 0.92f).toInt().coerceAtMost((460 * displayMetrics.density).toInt())

        panelParams = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        panelView = ComposeView(this).apply {
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
                        onCaptureScreenRequested = {
                            val intent = Intent(this@OverlayService, ScreenCaptureActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                        },
                        onPickImageRequested = {
                            val intent = Intent(this@OverlayService, MainActivity::class.java).apply {
                                action = MainActivity.ACTION_PICK_IMAGE
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(intent)
                        },
                        onMinimizeRequested = {
                            OverlayStateManager.setPanelExpanded(false)
                        },
                        onCloseRequested = {
                            OverlayStateManager.setPanelExpanded(false)
                        },
                        onOpenSettingsRequested = {
                            val intent = Intent(this@OverlayService, MainActivity::class.java).apply {
                                action = MainActivity.ACTION_OPEN_SETTINGS
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    private fun observeState() {
        combine(OverlayStateManager.isPanelExpanded, OverlayStateManager.isCapturing) { expanded, capturing ->
            expanded to capturing
        }.onEach { (expanded, capturing) ->
            if (capturing) {
                if (bubbleView?.isAttachedToWindow == true) runCatching { windowManager.removeView(bubbleView) }
                if (panelView?.isAttachedToWindow == true) runCatching { windowManager.removeView(panelView) }
                return@onEach
            }
            if (expanded) {
                // Show panel, hide bubble
                if (bubbleView?.isAttachedToWindow == true) {
                    runCatching { windowManager.removeView(bubbleView) }
                }
                if (panelView?.isAttachedToWindow == false) {
                    runCatching { windowManager.addView(panelView, panelParams) }.onFailure {
                        OverlayStateManager.setErrorMessage("Could not display Peek. Check overlay permission.")
                        stopSelf()
                    }
                }
            } else {
                // Show bubble, hide panel
                if (panelView?.isAttachedToWindow == true) {
                    runCatching { windowManager.removeView(panelView) }
                }
                if (bubbleView?.isAttachedToWindow == false) {
                    runCatching { windowManager.addView(bubbleView, bubbleParams) }.onFailure {
                        OverlayStateManager.setErrorMessage("Could not display Peek. Check overlay permission.")
                        stopSelf()
                    }
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
            .setContentText("Tap to open Peek or use the floating bubble")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Overlay", pendingStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        OverlayStateManager.setServiceRunning(false)
        OverlayStateManager.setPanelExpanded(false)
        OverlayStateManager.cancelRequest()

        scope.cancel()
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
