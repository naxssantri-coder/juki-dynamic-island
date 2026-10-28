package com.dynisland.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dynisland.app.IslandApp
import com.dynisland.app.MainActivity
import com.dynisland.app.R
import com.dynisland.app.data.IslandPosition
import com.dynisland.app.domain.IslandMode
import com.dynisland.app.notification.NotificationAccess
import com.dynisland.app.sources.BluetoothPermission
import com.dynisland.app.sources.BluetoothSource
import com.dynisland.app.sources.ChargingSource
import com.dynisland.app.sources.MediaSource
import com.dynisland.app.sources.SourceManager
import com.dynisland.app.ui.island.IslandDimens
import com.dynisland.app.ui.island.IslandOverlayContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Hosts the overlay and the event sources. A foreground service is needed so Android keeps the
 * process alive while the app is in the background (background-service limits since Android 8).
 * It does no work while idle: no polling, no running animations, and the window is only attached
 * while an event is visible. Event sources hold listeners only while their feature is on.
 */
class IslandOverlayService : Service() {

    private val app get() = application as IslandApp
    private var scope: CoroutineScope? = null
    private var overlay: OverlayWindow? = null
    private var layoutJob: Job? = null
    private var sourceManager: SourceManager? = null
    private var screenReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        enterForeground()

        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        overlay = OverlayWindow(this) { app.islandController.collapse() }

        val controller = app.islandController
        sourceManager = SourceManager(
            app.settings,
            listOf(
                SourceManager.Entry(
                    name = "music",
                    source = MediaSource(this, controller, serviceScope),
                    wanted = { it.musicEnabled },
                    permitted = { NotificationAccess.isEnabled(this) },
                ),
                SourceManager.Entry(
                    name = "charging",
                    source = ChargingSource(this, controller),
                    wanted = { it.chargingEnabled },
                    permitted = { true },
                ),
                SourceManager.Entry(
                    name = "bluetooth",
                    source = BluetoothSource(this, controller),
                    wanted = { it.bluetoothEnabled },
                    permitted = { BluetoothPermission.isGranted(this) },
                ),
            ),
        )

        serviceScope.launch {
            controller.state.map { it.mode }.distinctUntilChanged().collect { onModeChanged(it) }
        }
        serviceScope.launch {
            app.settings.map { it.position }.distinctUntilChanged().collect { relayout() }
        }
        serviceScope.launch {
            // Re-evaluate which sources should run whenever any setting changes.
            app.settings.collect { sourceManager?.refresh() }
        }
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            app.appScope.launch { app.settingsRepository.update { it.copy(islandEnabled = false) } }
            stopSelf()
            return START_NOT_STICKY
        }
        // Called every time the app comes to the foreground: picks up newly granted permissions.
        sourceManager?.refresh()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        relayout()
    }

    override fun onDestroy() {
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        sourceManager?.stopAll()
        sourceManager = null
        layoutJob?.cancel()
        overlay?.hide()
        overlay = null
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private fun registerScreenReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    // Screen off: drop the island (and its window) so nothing lingers.
                    Intent.ACTION_SCREEN_OFF -> app.islandController.reset()
                    Intent.ACTION_SCREEN_ON -> app.timerManager.onScreenOn()
                }
            }
        }
        screenReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun onModeChanged(mode: IslandMode) {
        val window = overlay ?: return
        val position = app.settings.value.position
        layoutJob?.cancel()
        when (mode) {
            IslandMode.Hidden -> {
                layoutJob = scope?.launch {
                    delay(IslandDimens.ExitAnimationMs)
                    window.hide()
                }
            }
            IslandMode.Compact -> {
                if (!window.isShown) {
                    showWindow(window, position, expanded = false)
                } else {
                    // Coming back from Expanded: shrink the window only after the pill finished collapsing.
                    layoutJob = scope?.launch {
                        delay(IslandDimens.ExitAnimationMs)
                        window.update(position, expanded = false)
                    }
                }
            }
            IslandMode.Expanded -> {
                if (!window.isShown) showWindow(window, position, expanded = true)
                else window.update(position, expanded = true)
            }
        }
    }

    private fun showWindow(window: OverlayWindow, position: IslandPosition, expanded: Boolean) {
        val ok = window.show(position, expanded) {
            IslandOverlayContent(app.islandController, app.settings)
        }
        if (!ok) Log.w(TAG, "Overlay not shown (permission missing or window add failed)")
    }

    private fun relayout() {
        val window = overlay ?: return
        val expanded = app.islandController.state.value.mode == IslandMode.Expanded
        window.update(app.settings.value.position, expanded)
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Island service", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Keeps the island available above other apps"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun enterForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, IslandOverlayService::class.java).setAction(ACTION_STOP),
            flags,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_island_notif)
            .setContentTitle("Island is active")
            .setContentText("Tap to open settings")
            .setContentIntent(open)
            .addAction(0, "Turn off", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "IslandOverlayService"
        private const val CHANNEL_ID = "island_service"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.dynisland.app.action.STOP"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, IslandOverlayService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Could not start island service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IslandOverlayService::class.java))
        }
    }
}
