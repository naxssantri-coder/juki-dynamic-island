package com.dynisland.app.data

enum class AnimationIntensity { Subtle, Normal, Expressive }

enum class IslandPosition { Default, SlightlyLower }

enum class CompactDuration(val millis: Long) {
    Brief(2_500L),
    Normal(4_000L),
    Extended(6_500L),
}

data class IslandSettings(
    val islandEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
    val animationIntensity: AnimationIntensity = AnimationIntensity.Normal,
    val position: IslandPosition = IslandPosition.Default,
    val compactDuration: CompactDuration = CompactDuration.Normal,
    // Features
    val musicEnabled: Boolean = true,
    val chargingEnabled: Boolean = true,
    val timerEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val bluetoothEnabled: Boolean = true,
    // Notifications
    val allowedApps: Set<String> = emptySet(),
    val showNotificationPreview: Boolean = true,
    // General
    val startOnBoot: Boolean = false,
)
