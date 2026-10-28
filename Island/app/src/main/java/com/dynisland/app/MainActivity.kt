package com.dynisland.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.dynisland.app.notification.NotificationAccess
import com.dynisland.app.overlay.OverlayPermission
import com.dynisland.app.sources.BluetoothPermission
import com.dynisland.app.ui.main.MainActions
import com.dynisland.app.ui.main.MainScreen
import com.dynisland.app.ui.main.MainViewModel
import com.dynisland.app.ui.theme.IslandTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val postNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* service runs either way */ }

    private val bluetoothConnect =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onResumed() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val actions = MainActions(
            openOverlaySettings = { openOverlaySettings() },
            openNotificationAccess = { openNotificationAccess() },
            requestBluetooth = { requestBluetooth() },
            requestPostNotifications = { requestPostNotifications() },
        )
        setContent {
            IslandTheme { MainScreen(viewModel = viewModel, actions = actions) }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumed()
    }

    private fun openOverlaySettings() {
        try {
            startActivity(OverlayPermission.settingsIntent(this))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun openNotificationAccess() {
        try {
            startActivity(NotificationAccess.settingsIntent())
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun requestPostNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            postNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !BluetoothPermission.isGranted(this)) {
            bluetoothConnect.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
}
