package com.dynisland.app.domain

import android.graphics.Bitmap

enum class IslandEventType { Call, Timer, Music, Charging, Bluetooth, Notification, Test }

/** A button shown in the expanded panel (timer pause/stop, "Open", "Dismiss"...). */
data class IslandAction(val label: String, val onClick: () -> Unit)

/**
 * Transport controls for media events. A null callback means "this media app does not
 * support that action" and the UI shows the control dimmed instead of pretending it works.
 */
data class MediaControlsState(
    val isPlaying: Boolean,
    val onPrevious: (() -> Unit)?,
    val onPlayPause: (() -> Unit)?,
    val onNext: (() -> Unit)?,
)

/**
 * One thing the island can show. Producers (charging, media, timer, ...) build these;
 * the UI only knows how to render them.
 */
data class IslandEvent(
    val id: String,
    val type: IslandEventType,
    val icon: String,
    val compactText: String,
    val title: String,
    val subtitle: String? = null,
    val expandable: Boolean = true,
    val media: MediaControlsState? = null,
    /** Large centered text in the expanded panel (timer digits). */
    val bigText: String? = null,
    val actions: List<IslandAction> = emptyList(),
    val artwork: Bitmap? = null,
    /** Sticky events do not auto-hide from the compact state. */
    val sticky: Boolean = false,
    /** Overrides the user's compact duration for this event. */
    val durationMs: Long? = null,
)
