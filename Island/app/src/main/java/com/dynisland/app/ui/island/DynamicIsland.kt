package com.dynisland.app.ui.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynisland.app.data.IslandSettings
import com.dynisland.app.domain.IslandController
import com.dynisland.app.domain.IslandAction
import com.dynisland.app.domain.IslandEvent
import com.dynisland.app.domain.IslandMode
import com.dynisland.app.domain.IslandUiState
import com.dynisland.app.domain.MediaControlsState
import com.dynisland.app.ui.theme.IslandColors
import com.dynisland.app.ui.theme.IslandTheme
import kotlinx.coroutines.flow.StateFlow

private data class IslandShape(val width: Dp, val height: Dp, val radius: Dp, val alpha: Float)

/** Entry point used inside the overlay window. */
@Composable
fun IslandOverlayContent(controller: IslandController, settings: StateFlow<IslandSettings>) {
    IslandTheme {
        val state by controller.state.collectAsState()
        val currentSettings by settings.collectAsState()
        DynamicIslandHost(
            state = state,
            settings = currentSettings,
            onExpand = controller::expand,
            onCollapse = controller::collapse,
        )
    }
}

/**
 * The island. One pill whose width/height/corner radius are driven by springs, so compact and
 * expanded are the same shape morphing rather than two views swapping.
 */
@Composable
fun DynamicIslandHost(
    state: IslandUiState,
    settings: IslandSettings,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Start one frame "hidden" so a freshly created window animates in instead of popping in.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanosCompat()
        entered = true
    }

    val mode = if (entered) state.mode else IslandMode.Hidden
    val event = state.event
    val motion = remember(settings.animationsEnabled, settings.animationIntensity) { IslandMotion.from(settings) }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    val target = when (mode) {
        IslandMode.Hidden -> IslandShape(96.dp, 24.dp, 12.dp, 0f)
        IslandMode.Compact -> IslandShape(
            IslandDimens.CompactWidthDp.dp,
            IslandDimens.CompactHeightDp.dp,
            (IslandDimens.CompactHeightDp / 2).dp,
            1f,
        )
        IslandMode.Expanded -> IslandShape(
            IslandDimens.expandedWidthDp(screenWidthDp).dp,
            IslandDimens.expandedHeightDp(event).dp,
            34.dp,
            1f,
        )
    }

    val width by animateDpAsState(target.width, motion.spec<Dp>(), label = "islandWidth")
    val height by animateDpAsState(target.height, motion.spec<Dp>(), label = "islandHeight")
    val radius by animateDpAsState(target.radius, motion.spec<Dp>(), label = "islandRadius")
    val visibleAlpha by animateFloatAsState(target.alpha, motion.fade(180), label = "islandAlpha")

    val shape = RoundedCornerShape(radius.coerceAtLeast(0.dp))
    val noIndication = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .then(
                if (mode == IslandMode.Expanded) {
                    // Tapping the transparent area of the window around the panel collapses it.
                    Modifier.clickable(interactionSource = noIndication, indication = null) { onCollapse() }
                } else {
                    Modifier
                },
            )
            .padding(
                start = IslandDimens.HorizontalPadDp.dp,
                end = IslandDimens.HorizontalPadDp.dp,
                bottom = IslandDimens.BottomPadDp.dp,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .size(width.coerceAtLeast(0.dp), height.coerceAtLeast(0.dp))
                .graphicsLayer { this.alpha = visibleAlpha }
                .shadow(elevation = 10.dp, shape = shape, clip = false)
                .clip(shape)
                .background(IslandColors.Surface)
                .clickable { if (mode == IslandMode.Compact) onExpand() },
        ) {
            if (event != null) {
                val contentMode = if (mode == IslandMode.Expanded) IslandMode.Expanded else IslandMode.Compact
                AnimatedContent(
                    targetState = contentMode,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { fadeIn(motion.fade(200, 90)) togetherWith fadeOut(motion.fade(90)) },
                    label = "islandContent",
                ) { m ->
                    when (m) {
                        IslandMode.Expanded -> ExpandedIsland(event)
                        else -> CompactIsland(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactIsland(event: IslandEvent) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(event.icon, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = event.compactText,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ExpandedIsland(event: IslandEvent) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (event.bigText != null) BigTextHeader(event) else HeaderRow(event)
        event.media?.let { MediaControls(it) }
        if (event.actions.isNotEmpty()) ActionRow(event.actions)
    }
}

@Composable
private fun HeaderRow(event: IslandEvent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(IslandColors.Artwork),
            contentAlignment = Alignment.Center,
        ) {
            val art = event.artwork
            if (art != null) {
                val image = remember(art) { art.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(event.icon, fontSize = 24.sp)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = event.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            event.subtitle?.let {
                Text(
                    text = it,
                    color = IslandColors.Secondary,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BigTextHeader(event: IslandEvent) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = event.title, color = IslandColors.Secondary, fontSize = 13.sp)
        Text(
            text = event.bigText.orEmpty(),
            color = Color.White,
            fontSize = 44.sp,
            fontWeight = FontWeight.Light,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
private fun ActionRow(actions: List<IslandAction>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.forEach { action ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(IslandColors.Artwork)
                    .clickable { action.onClick() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(action.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Controls whose callback is null are dimmed and disabled: the media app does not support them. */
@Composable
private fun MediaControls(media: MediaControlsState) {
    val dim = Color(0x55FFFFFF)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { media.onPrevious?.invoke() }, enabled = media.onPrevious != null) {
            SkipIcon(forward = false, color = if (media.onPrevious != null) Color.White else dim)
        }
        IconButton(onClick = { media.onPlayPause?.invoke() }, enabled = media.onPlayPause != null) {
            PlayPauseIcon(playing = media.isPlaying, color = if (media.onPlayPause != null) Color.White else dim)
        }
        IconButton(onClick = { media.onNext?.invoke() }, enabled = media.onNext != null) {
            SkipIcon(forward = true, color = if (media.onNext != null) Color.White else dim)
        }
    }
}

private suspend fun withFrameNanosCompat() {
    androidx.compose.runtime.withFrameNanos { }
}
