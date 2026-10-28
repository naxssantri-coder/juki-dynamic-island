package com.dynisland.app.sources

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.dynisland.app.domain.IslandController
import com.dynisland.app.domain.IslandEvent
import com.dynisland.app.domain.IslandEventType

object BluetoothPermission {
    /** BLUETOOTH_CONNECT is a runtime permission only on Android 12+. */
    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
}

/** Listens to ACL connect/disconnect broadcasts (no polling). Needs BLUETOOTH_CONNECT on Android 12+. */
class BluetoothSource(
    private val context: Context,
    private val controller: IslandController,
) : EventSource {

    private var receiver: BroadcastReceiver? = null
    private val lastSeen = HashMap<String, Long>()

    override fun start() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = handle(intent)
        }
        receiver = r
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        lastSeen.clear()
        controller.remove(ID)
    }

    private fun handle(intent: Intent) {
        val device = intent.bluetoothDevice() ?: return
        val connected = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED

        // Debounce: some devices fire several ACL events in a row.
        val now = SystemClock.elapsedRealtime()
        val dedupeKey = device.address + connected
        if (now - (lastSeen[dedupeKey] ?: -DEBOUNCE_MS) < DEBOUNCE_MS) return
        lastSeen[dedupeKey] = now

        val info = try {
            device.bluetoothClass?.majorDeviceClass to device.name
        } catch (e: SecurityException) {
            return // permission revoked while running
        }
        val major = info.first
        val name = info.second
        // Skip anonymous BLE chatter (trackers, beacons). Only show device types people care about.
        if (major == null || major !in SHOWN_CLASSES) return

        val label = name?.takeIf { it.isNotBlank() } ?: "Bluetooth device"
        val audio = major == BluetoothClass.Device.Major.AUDIO_VIDEO
        controller.post(
            IslandEvent(
                id = ID,
                type = IslandEventType.Bluetooth,
                icon = if (audio) "🎧" else "🔗",
                compactText = if (connected) "$label Connected" else "Device Disconnected",
                title = label,
                subtitle = if (connected) "Connected" else "Disconnected",
            ),
        )
    }

    @Suppress("DEPRECATION")
    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private companion object {
        const val ID = "bluetooth"
        const val DEBOUNCE_MS = 2_000L
        val SHOWN_CLASSES = setOf(
            BluetoothClass.Device.Major.AUDIO_VIDEO,
            BluetoothClass.Device.Major.PERIPHERAL,
            BluetoothClass.Device.Major.WEARABLE,
            BluetoothClass.Device.Major.PHONE,
        )
    }
}
