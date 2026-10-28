package com.dynisland.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.islandDataStore: DataStore<Preferences> by preferencesDataStore(name = "island_settings")

private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

/** Persists [IslandSettings] in Preferences DataStore. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val Enabled = booleanPreferencesKey("island_enabled")
        val Animations = booleanPreferencesKey("animations_enabled")
        val Intensity = stringPreferencesKey("animation_intensity")
        val Position = stringPreferencesKey("position")
        val Duration = stringPreferencesKey("compact_duration")
        val Music = booleanPreferencesKey("feature_music")
        val Charging = booleanPreferencesKey("feature_charging")
        val Timer = booleanPreferencesKey("feature_timer")
        val Notifications = booleanPreferencesKey("feature_notifications")
        val Bluetooth = booleanPreferencesKey("feature_bluetooth")
        val AllowedApps = stringSetPreferencesKey("allowed_apps")
        val NotificationPreview = booleanPreferencesKey("notification_preview")
        val StartOnBoot = booleanPreferencesKey("start_on_boot")
    }

    val settings: Flow<IslandSettings> = context.islandDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs.toSettings() }

    suspend fun update(transform: (IslandSettings) -> IslandSettings) {
        context.islandDataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.Enabled] = next.islandEnabled
            prefs[Keys.Animations] = next.animationsEnabled
            prefs[Keys.Intensity] = next.animationIntensity.name
            prefs[Keys.Position] = next.position.name
            prefs[Keys.Duration] = next.compactDuration.name
            prefs[Keys.Music] = next.musicEnabled
            prefs[Keys.Charging] = next.chargingEnabled
            prefs[Keys.Timer] = next.timerEnabled
            prefs[Keys.Notifications] = next.notificationsEnabled
            prefs[Keys.Bluetooth] = next.bluetoothEnabled
            prefs[Keys.AllowedApps] = next.allowedApps
            prefs[Keys.NotificationPreview] = next.showNotificationPreview
            prefs[Keys.StartOnBoot] = next.startOnBoot
        }
    }

    private fun Preferences.toSettings() = IslandSettings(
        islandEnabled = this[Keys.Enabled] ?: true,
        animationsEnabled = this[Keys.Animations] ?: true,
        animationIntensity = enumOrDefault(this[Keys.Intensity], AnimationIntensity.Normal),
        position = enumOrDefault(this[Keys.Position], IslandPosition.Default),
        compactDuration = enumOrDefault(this[Keys.Duration], CompactDuration.Normal),
        musicEnabled = this[Keys.Music] ?: true,
        chargingEnabled = this[Keys.Charging] ?: true,
        timerEnabled = this[Keys.Timer] ?: true,
        notificationsEnabled = this[Keys.Notifications] ?: true,
        bluetoothEnabled = this[Keys.Bluetooth] ?: true,
        allowedApps = this[Keys.AllowedApps] ?: emptySet(),
        showNotificationPreview = this[Keys.NotificationPreview] ?: true,
        startOnBoot = this[Keys.StartOnBoot] ?: false,
    )
}
