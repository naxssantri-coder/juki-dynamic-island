package com.dynisland.app.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dynisland.app.IslandApp
import com.dynisland.app.overlay.IslandOverlayService
import com.dynisland.app.overlay.OverlayPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Starts the island after reboot, only if the user turned "Start on boot" on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? IslandApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val s = app.settingsRepository.settings.first()
                if (s.islandEnabled && s.startOnBoot && OverlayPermission.isGranted(app)) {
                    IslandOverlayService.start(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
