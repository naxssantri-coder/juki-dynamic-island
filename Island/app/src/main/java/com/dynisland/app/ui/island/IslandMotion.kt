package com.dynisland.app.ui.island

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import com.dynisland.app.data.AnimationIntensity
import com.dynisland.app.data.IslandSettings

/** Turns the user's animation settings into concrete animation specs. */
@Immutable
data class IslandMotion(
    val enabled: Boolean,
    val damping: Float,
    val stiffness: Float,
) {
    fun <T> spec(): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = damping, stiffness = stiffness) else snap()

    fun fade(durationMs: Int, delayMs: Int = 0): FiniteAnimationSpec<Float> =
        if (enabled) tween(durationMillis = durationMs, delayMillis = delayMs) else snap()

    companion object {
        fun from(settings: IslandSettings): IslandMotion = when (settings.animationIntensity) {
            AnimationIntensity.Subtle -> IslandMotion(settings.animationsEnabled, 1.0f, 600f)
            AnimationIntensity.Normal -> IslandMotion(settings.animationsEnabled, 0.78f, 420f)
            AnimationIntensity.Expressive -> IslandMotion(settings.animationsEnabled, 0.6f, 300f)
        }
    }
}
