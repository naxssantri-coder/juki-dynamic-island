package com.dynisland.app.sources

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.dynisland.app.domain.IslandController
import com.dynisland.app.domain.IslandEvent
import com.dynisland.app.domain.IslandEventType

/**
 * Event-driven charging detection. The cheap plug/unplug broadcasts stay registered; the
 * battery-level broadcast is registered ONLY while plugged in, and ignores anything that is
 * not a whole-percent change.
 */
class ChargingSource(
    private val context: Context,
    private val controller: IslandController,
) : EventSource {

    private var plugReceiver: BroadcastReceiver? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var lastPercent = -1
    private var fullShown = false
    private var silentFirstDelivery = false

    override fun start() {
        if (plugReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> onPlugged()
                    Intent.ACTION_POWER_DISCONNECTED -> onUnplugged()
                }
            }
        }
        plugReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Already plugged in when the service starts: track silently, don't pop the island.
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null && sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0) {
            startBatteryUpdates(silent = true)
        }
    }

    override fun stop() {
        plugReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        plugReceiver = null
        stopBatteryUpdates()
        controller.remove(CHARGING_ID)
        controller.remove(FULL_ID)
    }

    private fun onPlugged() {
        fullShown = false
        lastPercent = -1
        startBatteryUpdates(silent = false)
    }

    private fun onUnplugged() {
        stopBatteryUpdates()
        lastPercent = -1
        controller.remove(CHARGING_ID)
        controller.remove(FULL_ID)
    }

    private fun startBatteryUpdates(silent: Boolean) {
        if (batteryReceiver != null) return
        silentFirstDelivery = silent
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val silentNow = silentFirstDelivery
                silentFirstDelivery = false
                onBattery(intent, silentNow)
            }
        }
        batteryReceiver = receiver
        // ACTION_BATTERY_CHANGED is sticky: registering delivers the current state immediately.
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun stopBatteryUpdates() {
        batteryReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        batteryReceiver = null
    }

    private fun onBattery(intent: Intent, silent: Boolean) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return // battery state unavailable
        val percent = level * 100 / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

        if (status == BatteryManager.BATTERY_STATUS_FULL) {
            if (!fullShown) {
                fullShown = true
                controller.remove(CHARGING_ID)
                if (!silent) controller.post(fullEvent())
            }
            return
        }
        if (percent == lastPercent) return // cheap exit for temperature/voltage-only broadcasts
        val first = lastPercent == -1
        lastPercent = percent
        val event = chargingEvent(percent, intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0))
        if (first) {
            if (!silent) controller.post(event)
        } else {
            controller.updateIfShowing(event)
        }
    }

    private fun chargingEvent(percent: Int, plugged: Int): IslandEvent {
        val source = when {
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Wireless"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "Charger"
            else -> null
        }
        return IslandEvent(
            id = CHARGING_ID,
            type = IslandEventType.Charging,
            icon = "⚡",
            compactText = "Charging $percent%",
            title = "Charging",
            subtitle = if (source != null) "$percent% · $source" else "$percent%",
        )
    }

    private fun fullEvent() = IslandEvent(
        id = FULL_ID,
        type = IslandEventType.Charging,
        icon = "✓",
        compactText = "Fully Charged",
        title = "Fully Charged",
        subtitle = "100%",
    )

    private companion object {
        const val CHARGING_ID = "charging"
        const val FULL_ID = "charging-full"
    }
}
