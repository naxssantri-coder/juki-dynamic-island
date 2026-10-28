package com.dynisland.app.overlay

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import com.dynisland.app.data.IslandPosition
import com.dynisland.app.ui.island.IslandDimens
import kotlin.math.max
import kotlin.math.roundToInt

/** Derives the pill's top offset from real insets/cutout data. No hardcoded pixel positions. */
object IslandPlacement {

    fun topPx(context: Context, position: IslandPosition): Int {
        val density = context.resources.displayMetrics.density
        val pillHeightPx = IslandDimens.CompactHeightDp * density
        var statusBarPx = fallbackStatusBarHeight(context)
        var cutout: Rect? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val insets = wm.currentWindowMetrics.windowInsets
                val bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
                if (bars.top > 0) statusBarPx = bars.top
                val rect = insets.displayCutout?.boundingRectTop
                if (rect != null && !rect.isEmpty) cutout = rect
            } catch (e: Exception) {
                // keep fallback values
            }
        }

        val top: Float = when (position) {
            // Vertically centered on the camera cutout, or on the status bar if there is none.
            IslandPosition.Default -> (cutout?.exactCenterY() ?: (statusBarPx / 2f)) - pillHeightPx / 2f
            // Sits fully below the status bar / cutout so it never overlaps the camera.
            IslandPosition.SlightlyLower -> max(statusBarPx, cutout?.bottom ?: 0) + 4 * density
        }
        return top.roundToInt().coerceAtLeast((4 * density).roundToInt())
    }

    private fun fallbackStatusBarHeight(context: Context): Int {
        val res = context.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else (24 * res.displayMetrics.density).roundToInt()
    }
}
