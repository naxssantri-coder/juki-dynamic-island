package com.dynisland.app.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dynisland.app.data.AnimationIntensity
import com.dynisland.app.data.CompactDuration
import com.dynisland.app.data.IslandPosition
import com.dynisland.app.domain.IslandMode
import com.dynisland.app.timer.TimerManager
import com.dynisland.app.timer.TimerState
import com.dynisland.app.ui.island.DynamicIslandHost
import java.text.DateFormat
import java.util.Date

class MainActions(
    val openOverlaySettings: () -> Unit,
    val openNotificationAccess: () -> Unit,
    val requestBluetooth: () -> Unit,
    val requestPostNotifications: () -> Unit,
)

private enum class Screen { Main, Apps }

@Composable
fun MainScreen(viewModel: MainViewModel, actions: MainActions) {
    var screen by rememberSaveable { mutableStateOf(Screen.Main) }
    BackHandler(enabled = screen == Screen.Apps) { screen = Screen.Main }

    when (screen) {
        Screen.Main -> MainContent(viewModel, actions, onChooseApps = { screen = Screen.Apps })
        Screen.Apps -> AppsScreen(viewModel, onBack = { screen = Screen.Main })
    }
}

@Composable
private fun MainContent(viewModel: MainViewModel, actions: MainActions, onChooseApps: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val islandState by viewModel.islandState.collectAsStateWithLifecycle()
    val overlayGranted by viewModel.overlayGranted.collectAsStateWithLifecycle()
    val notificationAccess by viewModel.notificationAccess.collectAsStateWithLifecycle()
    val bluetoothGranted by viewModel.bluetoothGranted.collectAsStateWithLifecycle()
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Island", style = MaterialTheme.typography.headlineLarge)

            // Overlay permission
            SectionCard {
                Text("Overlay permission", style = MaterialTheme.typography.titleMedium)
                if (overlayGranted) {
                    SecondaryText("Granted. The island can appear above other apps.")
                } else {
                    SecondaryText(
                        "The island is drawn above other apps, which Android only allows with the " +
                            "“Display over other apps” permission. Without it the island only shows " +
                            "inside this app (limited mode).",
                    )
                    Button(onClick = actions.openOverlaySettings) { Text("Grant permission") }
                }
            }

            // General
            SectionCard {
                Text("General", style = MaterialTheme.typography.titleMedium)
                SwitchRow(
                    title = "Dynamic Island",
                    subtitle = "Runs a small foreground service while enabled",
                    checked = settings.islandEnabled,
                    onCheckedChange = {
                        viewModel.setIslandEnabled(it)
                        if (it) actions.requestPostNotifications()
                    },
                )
                SwitchRow(
                    title = "Start on boot",
                    subtitle = "Start the island after the phone restarts",
                    checked = settings.startOnBoot,
                    onCheckedChange = viewModel::setStartOnBoot,
                )
                SwitchRow(
                    title = "Animations",
                    subtitle = null,
                    checked = settings.animationsEnabled,
                    onCheckedChange = viewModel::setAnimationsEnabled,
                )
                if (settings.animationsEnabled) {
                    ChipGroup(
                        title = "Animation intensity",
                        options = AnimationIntensity.entries,
                        selected = settings.animationIntensity,
                        label = { it.name },
                        onSelect = viewModel::setIntensity,
                    )
                }
            }

            // Appearance
            SectionCard {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                ChipGroup(
                    title = "Island position",
                    options = IslandPosition.entries,
                    selected = settings.position,
                    label = { if (it == IslandPosition.Default) "Default" else "Slightly lower" },
                    onSelect = viewModel::setPosition,
                )
                SecondaryText("If the text overlaps your camera cutout, try “Slightly lower”.")
                ChipGroup(
                    title = "Compact duration",
                    options = CompactDuration.entries,
                    selected = settings.compactDuration,
                    label = {
                        when (it) {
                            CompactDuration.Brief -> "Short"
                            CompactDuration.Normal -> "Normal"
                            CompactDuration.Extended -> "Long"
                        }
                    },
                    onSelect = viewModel::setCompactDuration,
                )
            }

            // Features
            SectionCard {
                Text("Features", style = MaterialTheme.typography.titleMedium)
                SwitchRow("Music", "Any app with a media session", settings.musicEnabled, viewModel::setMusic)
                SwitchRow("Charging", null, settings.chargingEnabled, viewModel::setCharging)
                SwitchRow("Timer", null, settings.timerEnabled, viewModel::setTimer)
                SwitchRow(
                    title = "Notifications",
                    subtitle = "Only apps you choose",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = viewModel::setNotifications,
                )
                SwitchRow(
                    title = "Bluetooth",
                    subtitle = if (bluetoothGranted) null else "Needs Bluetooth permission",
                    checked = settings.bluetoothEnabled,
                    onCheckedChange = {
                        viewModel.setBluetooth(it)
                        if (it && !bluetoothGranted) actions.requestBluetooth()
                    },
                )
                if (settings.bluetoothEnabled && !bluetoothGranted) {
                    OutlinedButton(onClick = actions.requestBluetooth) { Text("Grant Bluetooth permission") }
                }
            }

            // Notification access (music + notifications)
            if (settings.musicEnabled || settings.notificationsEnabled) {
                SectionCard {
                    Text("Notification access", style = MaterialTheme.typography.titleMedium)
                    if (notificationAccess) {
                        SecondaryText("Enabled. Music and notifications can work.")
                    } else {
                        SecondaryText(
                            "Android requires Notification access to read media sessions and notifications. " +
                                "Without it, Music and Notifications stay off; everything else still works.",
                        )
                        SecondaryText(
                            "If the switch is greyed out (Android 13+ for apps installed outside the Play Store): " +
                                "open Settings > Apps > Island, tap the ⋮ menu and choose “Allow restricted settings”.",
                        )
                        Button(onClick = actions.openNotificationAccess) { Text("Open notification access") }
                    }
                    if (settings.notificationsEnabled) {
                        OutlinedButton(onClick = onChooseApps, modifier = Modifier.fillMaxWidth()) {
                            Text("Choose apps (${settings.allowedApps.size} allowed)")
                        }
                        SwitchRow(
                            title = "Show message text",
                            subtitle = "When off, only “New notification” is shown. Always hidden while locked.",
                            checked = settings.showNotificationPreview,
                            onCheckedChange = viewModel::setNotificationPreview,
                        )
                    }
                }
            }

            // Timer
            if (settings.timerEnabled) {
                TimerCard(timerState, viewModel)
            }

            // Test
            SectionCard {
                Text("Test", style = MaterialTheme.typography.titleMedium)
                SecondaryText("Demo events with fake data.")
                FilledTonalButton(
                    onClick = viewModel::showCompactDemo,
                    enabled = settings.islandEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Show compact") }
                FilledTonalButton(
                    onClick = viewModel::showExpandedDemo,
                    enabled = settings.islandEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Show expanded") }
                FilledTonalButton(
                    onClick = viewModel::showChargingDemo,
                    enabled = settings.islandEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Show charging demo") }
            }
        }

        // Limited mode: no overlay permission, so render the island inside the app instead.
        if (!overlayGranted) {
            if (islandState.mode == IslandMode.Expanded) {
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier.fillMaxSize().clickable(interactionSource = interaction, indication = null) {
                        viewModel.collapse()
                    },
                )
            }
            DynamicIslandHost(
                state = islandState,
                settings = settings,
                onExpand = viewModel::expand,
                onCollapse = viewModel::collapse,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
            )
        }
    }
}

@Composable
private fun TimerCard(timerState: TimerState, viewModel: MainViewModel) {
    var minutes by rememberSaveable { mutableIntStateOf(5) }
    SectionCard {
        Text("Timer", style = MaterialTheme.typography.titleMedium)
        when (timerState) {
            is TimerState.Running -> {
                val endsAt = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timerState.endsAtWall))
                SecondaryText("Running, ends at $endsAt")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = viewModel::pauseTimer) { Text("Pause") }
                    OutlinedButton(onClick = viewModel::stopTimer) { Text("Stop") }
                }
            }
            is TimerState.Paused -> {
                SecondaryText("Paused, ${TimerManager.format(timerState.remainingMs)} left")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = viewModel::resumeTimer) { Text("Resume") }
                    OutlinedButton(onClick = viewModel::stopTimer) { Text("Stop") }
                }
            }
            else -> {
                ChipGroup(
                    title = "Duration",
                    options = listOf(1, 5, 10, 25),
                    selected = minutes,
                    label = { "$it min" },
                    onSelect = { minutes = it },
                )
                Button(onClick = { viewModel.startTimer(minutes) }) { Text("Start timer") }
            }
        }
    }
}

@Composable
private fun AppsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadApps() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Notification apps", style = MaterialTheme.typography.titleLarge)
        }
        if (apps.isEmpty()) {
            SecondaryText("Loading apps…", modifier = Modifier.padding(20.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                items(apps, key = { it.packageName }) { entry ->
                    SwitchRow(
                        title = entry.label,
                        subtitle = entry.packageName,
                        checked = entry.packageName in settings.allowedApps,
                        onCheckedChange = { viewModel.toggleApp(entry.packageName, it) },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun SecondaryText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) SecondaryText(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> ChipGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}
