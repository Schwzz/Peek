package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Owns screen permission independently of every Activity and of individual screenshots. */
class MediaProjectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var source: ScreenCaptureHelper? = null
    private var captureJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Peek screen access", NotificationManager.IMPORTANCE_LOW)
            )
        }
        OverlayStateManager.isCapturing.onEach { if (!it) captureJob?.cancel() }.launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (source != null) return START_NOT_STICKY
        val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent::class.java)
        if (code != Activity.RESULT_OK || data == null) {
            OverlayStateManager.setStartingScreenSharing(false)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val stop = PendingIntent.getService(this, 2, Intent(this, MediaProjectionService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Peek screen access is active")
                .setContentText("Tap the bubble to select a region. Hold it to open chat.")
                .setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop screen access", stop).build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(NOTIFICATION_ID, notification)
            val projection = (getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(code, data)
                ?: error("Android did not grant screen access.")
            source = ScreenCaptureHelper(this, projection) { stopSelf() }
            active = this
            OverlayStateManager.setScreenSharing(true)
            OverlayStateManager.setStartingScreenSharing(false)
            OverlayStateManager.setStatusMessage("Ready over other apps. Tap the bubble to select; hold it for chat.")
            if (intent.getBooleanExtra(EXTRA_CAPTURE_WHEN_READY, false)) {
                scope.launch { delay(400); OverlayService.captureScreen(this@MediaProjectionService) }
            }
        } catch (e: Exception) {
            OverlayStateManager.setErrorMessage("Could not enable screen access: ${e.localizedMessage}")
            OverlayStateManager.setStartingScreenSharing(false)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun captureRegion() {
        val session = source ?: return
        if (captureJob?.isActive == true) return
        val version = OverlayStateManager.captureVersion
        val requestedAt = OverlayStateManager.captureRequestedAt
        captureJob = scope.launch {
            try {
                val bitmap = session.capture(requestedAt)
                if (OverlayStateManager.isCapturing.value && OverlayStateManager.captureVersion == version)
                    OverlayStateManager.setCapturedScreenToCrop(bitmap)
                else bitmap.recycle()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (OverlayStateManager.captureVersion == version) {
                    OverlayStateManager.setErrorMessage("Could not capture this screen: ${e.localizedMessage}")
                    OverlayStateManager.finishCapture(showPanel = true)
                }
            }
            // Screen sharing remains active after success, cancellation, or a failed screenshot.
        }
    }

    override fun onDestroy() {
        if (active === this) active = null
        captureJob?.cancel()
        source?.close(); source = null
        OverlayStateManager.setScreenSharing(false)
        OverlayStateManager.setStartingScreenSharing(false)
        if (OverlayStateManager.isCapturing.value) OverlayStateManager.finishCapture(showPanel = false)
        OverlayStateManager.setStatusMessage("Screen access stopped. Tap the bubble to reconnect.")
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "peek_capture_channel"
        private const val NOTIFICATION_ID = 2002
        const val ACTION_STOP = "com.example.capture.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "consent_data"
        private const val EXTRA_CAPTURE_WHEN_READY = "capture_when_ready"
        private var active: MediaProjectionService? = null

        fun requestFrame(): Boolean {
            val service = active ?: return false
            if (service.source == null) return false
            service.captureRegion()
            return true
        }

        fun startSession(context: Context, resultCode: Int, data: Intent, captureWhenReady: Boolean) {
            val intent = Intent(context, MediaProjectionService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode).putExtra(EXTRA_DATA, data)
                .putExtra(EXTRA_CAPTURE_WHEN_READY, captureWhenReady)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
