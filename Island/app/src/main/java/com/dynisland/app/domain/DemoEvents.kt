package com.dynisland.app.domain

/** Fake events for the test buttons. These are NOT real system events. */
object DemoEvents {
    fun music(playing: Boolean, onToggle: () -> Unit) = IslandEvent(
        id = "demo-music",
        type = IslandEventType.Test,
        icon = "🎵",
        compactText = "Midnight Drive",
        title = "Midnight Drive",
        subtitle = "Demo Artist",
        media = MediaControlsState(
            isPlaying = playing,
            onPrevious = { },
            onPlayPause = onToggle,
            onNext = { },
        ),
    )

    fun charging() = IslandEvent(
        id = "demo-charging",
        type = IslandEventType.Test,
        icon = "⚡",
        compactText = "Charging 42%",
        title = "Charging",
        subtitle = "42% · demo event",
    )
}
