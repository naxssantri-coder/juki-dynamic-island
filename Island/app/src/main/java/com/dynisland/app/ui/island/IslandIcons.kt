package com.dynisland.app.ui.island

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

/** Hand-drawn transport icons so we don't pull in the large material-icons-extended artifact. */
@Composable
fun PlayPauseIcon(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        if (playing) {
            val barW = w * 0.22f
            val radius = CornerRadius(barW * 0.3f)
            drawRoundRect(color, Offset(w * 0.22f, h * 0.14f), Size(barW, h * 0.72f), radius)
            drawRoundRect(color, Offset(w * 0.56f, h * 0.14f), Size(barW, h * 0.72f), radius)
        } else {
            val path = Path().apply {
                moveTo(w * 0.26f, h * 0.12f)
                lineTo(w * 0.86f, h * 0.5f)
                lineTo(w * 0.26f, h * 0.88f)
                close()
            }
            drawPath(path, color)
        }
    }
}

@Composable
fun SkipIcon(forward: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        scale(scaleX = if (forward) 1f else -1f, scaleY = 1f, pivot = center) {
            val path = Path().apply {
                moveTo(w * 0.12f, h * 0.2f)
                lineTo(w * 0.12f, h * 0.8f)
                lineTo(w * 0.66f, h * 0.5f)
                close()
            }
            drawPath(path, color)
            drawRoundRect(color, Offset(w * 0.74f, h * 0.2f), Size(w * 0.13f, h * 0.6f), CornerRadius(w * 0.04f))
        }
    }
}
