package com.dynisland.app.ui.main

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dynisland.app.IslandApp
import com.dynisland.app.data.AnimationIntensity
import com.dynisland.app.data.CompactDuration
import com.dynisland.app.data.IslandPosition
import com.dynisland.app.data.IslandSettings
import com.dynisland.app.domain.DemoEvents
import com.dynisland.app.domain.IslandUiState
import com.dynisland.app.notification.NotificationAccess
import com.dynisland.app.overlay.IslandOverlayService
import com.dynisland.app.overlay.OverlayPermission
import com.dynisland.app.sources.BluetoothPermission
import com.dynisland.app.timer.TimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppEntry(val label: String, val packageName: String)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as IslandApp
    private val controller = app.islandController

    val settings: StateFlow<IslandSettings> = app.settings
    val islandState: StateFlow<IslandUiState> = controller.state
    val timerState: StateFlow<TimerState> = app.timerManager.state

    private val _overlayGranted = MutableStateFlow(OverlayPermission.isGranted(application))
    val overlayGranted: StateFlow<Boolean> = _overlayGranted.asStateFlow()

    private val _notificationAccess = MutableStateFlow(NotificationAccess.isEnabled(application))
    val notificationAccess: StateFlow<Boolean> = _notificationAccess.asStateFlow()

    private val _bluetoothGranted = MutableStateFlow(BluetoothPermission.isGranted(application))
    val bluetoothGranted: StateFlow<Boolean> = _bluetoothGranted.asStateFlow()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    /** Called from Activity.onResume: re-check permissions (user may have just granted one) and sync the service. */
    fun onResumed() {
        val granted = OverlayPermission.isGranted(app)
        _overlayGranted.value = granted
        _notificationAccess.value = NotificationAccess.isEnabled(app)
        _bluetoothGranted.value = BluetoothPermission.isGranted(app)
        // start() is idempotent; calling it also makes a running service re-check its sources.
        if (granted && settings.value.islandEnabled) IslandOverlayService.start(app)
    }

    fun setIslandEnabled(enabled: Boolean) {
        update { it.copy(islandEnabled = enabled) }
        if (!enabled) {
            IslandOverlayService.stop(app)
        } else if (_overlayGranted.value) {
            IslandOverlayService.start(app)
        }
    }

    fun setAnimationsEnabled(value: Boolean) = update { it.copy(animationsEnabled = value) }
    fun setIntensity(value: AnimationIntensity) = update { it.copy(animationIntensity = value) }
    fun setPosition(value: IslandPosition) = update { it.copy(position = value) }
    fun setCompactDuration(value: CompactDuration) = update { it.copy(compactDuration = value) }
    fun setMusic(value: Boolean) = update { it.copy(musicEnabled = value) }
    fun setCharging(value: Boolean) = update { it.copy(chargingEnabled = value) }
    fun setTimer(value: Boolean) = update { it.copy(timerEnabled = value) }
    fun setNotifications(value: Boolean) = update { it.copy(notificationsEnabled = value) }
    fun setBluetooth(value: Boolean) = update { it.copy(bluetoothEnabled = value) }
    fun setNotificationPreview(value: Boolean) = update { it.copy(showNotificationPreview = value) }
    fun setStartOnBoot(value: Boolean) = update { it.copy(startOnBoot = value) }

    fun toggleApp(packageName: String, allowed: Boolean) = update {
        it.copy(allowedApps = if (allowed) it.allowedApps + packageName else it.allowedApps - packageName)
    }

    // Timer
    fun startTimer(minutes: Int) {
        if (_overlayGranted.value) IslandOverlayService.start(app)
        app.timerManager.start(minutes * 60_000L)
    }
    fun pauseTimer() = app.timerManager.pause()
    fun resumeTimer() = app.timerManager.resume()
    fun stopTimer() = app.timerManager.stop()

    // Demo events (fake data)
    fun showCompactDemo() = postDemoMusic(playing = true)

    fun showExpandedDemo() {
        postDemoMusic(playing = true)
        viewModelScope.launch {
            delay(700) // let the pill appear first so the expand animation is visible
            controller.expand()
        }
    }

    fun showChargingDemo() = controller.post(DemoEvents.charging())

    private fun postDemoMusic(playing: Boolean) {
        controller.post(DemoEvents.music(playing) { postDemoMusic(!playing) })
    }

    fun collapse() = controller.collapse()
    fun expand() = controller.expand()

    /** Lists launchable apps for the notification allow-list. Needs the LAUNCHER <queries> entry in the manifest. */
    @Suppress("DEPRECATION")
    fun loadApps() {
        if (_apps.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            val pm = app.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(intent, 0)
                .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.packageName != app.packageName }
                .distinctBy { it.packageName }
                .sortedWith(
                    compareByDescending<AppEntry> { it.packageName in SUGGESTED }
                        .thenBy { it.label.lowercase() },
                )
            _apps.value = list
        }
    }

    private fun update(transform: (IslandSettings) -> IslandSettings) {
        viewModelScope.launch { app.settingsRepository.update(transform) }
    }

    private companion object {
        // Package names from memory; apps that aren't installed simply don't appear.
        val SUGGESTED = setOf("com.whatsapp", "com.instagram.android", "org.telegram.messenger", "com.google.android.gm")
    }
}
