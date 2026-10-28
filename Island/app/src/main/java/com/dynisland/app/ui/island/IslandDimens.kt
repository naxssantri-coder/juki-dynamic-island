package com.dynisland.app.ui.island

import com.dynisland.app.domain.IslandEvent

/** Shared by the Compose UI and the window code so both agree on sizes. */
object IslandDimens {
    const val CompactWidthDp = 184
    const val CompactHeightDp = 36
    const val HorizontalPadDp = 12   // room for the shadow
    const val BottomPadDp = 16       // room for the shadow
    const val MaxExpandedWidthDp = 360
    const val ScreenMarginDp = 12
    const val ExitAnimationMs = 450L

    fun expandedWidthDp(screenWidthDp: Int): Int =
        minOf(MaxExpandedWidthDp, screenWidthDp - 2 * ScreenMarginDp)

    fun expandedHeightDp(event: IslandEvent?): Int = when {
        event == null -> 112
        event.media != null -> 176
        event.bigText != null -> 160
        event.actions.isNotEmpty() -> 152
        else -> 112
    }
}
