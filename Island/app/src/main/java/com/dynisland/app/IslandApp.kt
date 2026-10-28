package com.dynisland.app

import android.app.Application
import android.os.PowerManager
import com.dynisland.app.data.IslandSettings
import com.dynisland.app.data.SettingsRepository
import com.dynisland.app.domain.EventPriorityPolicy
import com.dynisland.app.domain.IslandController
import com.dynisland.app.domain.IslandEventType
import com.dynisland.app.notification.NotificationEvents
import com.dynisland.app.timer.TimerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Tiny manual DI container; swap for Hilt/Koin only if the app outgrows this. */
class IslandApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var settings: StateFlow<IslandSettings>
        private set
    lateinit var islandController: IslandController
        private set
    lateinit var timerManager: TimerManager
        private set
    lateinit var notificationEvents: NotificationEvents
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        settings = settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, IslandSettings())

        val power = getSystemService(PowerManager::class.java)
        islandController = IslandController(appScope, settings, EventPriorityPolicy.Default) { event ->
            // Screen off: nothing can be seen, so don't show anything (except things that must not be missed).
            power.isInteractive || event.type == IslandEventType.Timer || event.type == IslandEventType.Call
        }
        timerManager = TimerManager(this, appScope, islandController, settings)
        notificationEvents = NotificationEvents(this, islandController, settings)
    }
}
