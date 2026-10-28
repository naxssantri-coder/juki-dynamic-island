package com.dynisland.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dynisland.app.data.IslandPosition
import com.dynisland.app.ui.island.IslandDimens
import kotlin.math.roundToInt

/**
 * Owns the WindowManager overlay window. The window only exists while the island is visible,
 * and is WRAP_CONTENT while compact so it never blankets the screen.
 */
class OverlayWindow(
    private val context: Context,
    private val onOutsideTouch: () -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: OverlayRootView? = null
    private var owner: OverlayLifecycleOwner? = null

    val isShown: Boolean get() = rootView != null

    fun show(position: IslandPosition, expanded: Boolean, content: @Composable () -> Unit): Boolean {
        if (rootView != null) return true
        if (!OverlayPermission.isGranted(context)) return false

        val lifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        val root = OverlayRootView(context, onOutsideTouch)
        root.setViewTreeLifecycleOwner(lifecycleOwner)
        root.setViewTreeViewModelStoreOwner(lifecycleOwner)
        root.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        val compose = ComposeView(context).apply { setContent(content) }
        root.addView(
            compose,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        return try {
            windowManager.addView(root, buildLayoutParams(position, expanded))
            rootView = root
            owner = lifecycleOwner
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not add overlay window", e)
            lifecycleOwner.stop()
            false
        }
    }

    fun update(position: IslandPosition, expanded: Boolean) {
        val root = rootView ?: return
        try {
            windowManager.updateViewLayout(root, buildLayoutParams(position, expanded))
        } catch (e: Exception) {
            Log.w(TAG, "Could not update overlay window", e)
        }
    }

    fun hide() {
        val root = rootView ?: return
        try {
            windowManager.removeViewImmediate(root)
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove overlay window", e)
        }
        rootView = null
        owner?.stop()
        owner = null
    }

    private fun buildLayoutParams(position: IslandPosition, expanded: Boolean): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        val width = if (expanded) {
            val screenDp = context.resources.configuration.screenWidthDp
            ((IslandDimens.expandedWidthDp(screenDp) + 2 * IslandDimens.HorizontalPadDp) * density).roundToInt()
        } else {
            WindowManager.LayoutParams.WRAP_CONTENT
        }
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = IslandPlacement.topPx(context, position)
            title = "IslandOverlay"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private class OverlayRootView(
        context: Context,
        private val onOutsideTouch: () -> Unit,
    ) : FrameLayout(context) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.action == MotionEvent.ACTION_OUTSIDE) {
                onOutsideTouch()
                return true
            }
            return super.dispatchTouchEvent(ev)
        }
    }

    private companion object {
        const val TAG = "OverlayWindow"
    }
}
