package com.dynisland.app.sources

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.dynisland.app.domain.IslandController
import com.dynisland.app.domain.IslandEvent
import com.dynisland.app.domain.IslandEventType
import com.dynisland.app.domain.MediaControlsState
import com.dynisland.app.notification.IslandNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reads MediaSessions through MediaSessionManager. Android only allows this for an app that has
 * Notification access enabled, so [IslandNotificationListener] is used purely as the permission
 * anchor here. Works with any app that publishes a MediaSession; nothing is app-specific.
 */
class MediaSource(
    private val context: Context,
    private val controller: IslandController,
    private val scope: CoroutineScope,
) : EventSource {

    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, IslandNotificationListener::class.java)

    private val registered = HashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var sessions: List<MediaController> = emptyList()
    private var recomputeJob: Job? = null
    private var started = false
    private var firstRecompute = true
    private var lastKey: String? = null
    private var lastPlaying = false
    private var cachedArtKey: String? = null
    private var cachedArt: Bitmap? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> onSessions(list.orEmpty()) }

    override fun start() {
        if (started) return
        // Throws SecurityException when notification access is missing; SourceManager handles that.
        manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
        started = true
        firstRecompute = true
        onSessions(manager.getActiveSessions(listenerComponent))
    }

    override fun stop() {
        if (started) runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
        started = false
        recomputeJob?.cancel()
        registered.values.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        registered.clear()
        sessions = emptyList()
        lastKey = null
        lastPlaying = false
        cachedArt = null
        cachedArtKey = null
        controller.remove(ID)
    }

    private fun onSessions(list: List<MediaController>) {
        val tokens = list.map { it.sessionToken }.toSet()
        registered.keys.filter { it !in tokens }.forEach { token ->
            registered.remove(token)?.let { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        }
        for (c in list) {
            if (c.sessionToken in registered) continue
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = scheduleRecompute()
                override fun onMetadataChanged(metadata: MediaMetadata?) = scheduleRecompute()
                override fun onSessionDestroyed() = scheduleRecompute()
            }
            c.registerCallback(cb)
            registered[c.sessionToken] = c to cb
        }
        sessions = list
        scheduleRecompute()
    }

    /** Playback state and metadata callbacks usually arrive back to back; coalesce them. */
    private fun scheduleRecompute() {
        recomputeJob?.cancel()
        recomputeJob = scope.launch {
            delay(150)
            recompute()
        }
    }

    private fun recompute() {
        val silent = firstRecompute // don't pop the island for music that was already playing when we started
        firstRecompute = false

        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull {
                val st = it.playbackState?.state
                st == PlaybackState.STATE_PAUSED || st == PlaybackState.STATE_BUFFERING
            }
        val meta = best?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (best == null || meta == null || title.isNullOrBlank()) {
            lastKey = null
            lastPlaying = false
            controller.remove(ID) // playback stopped / media app unavailable
            return
        }

        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val state = best.playbackState
        val playing = state?.state == PlaybackState.STATE_PLAYING
        val key = "${best.packageName}|$title|$artist"
        val trackChanged = key != lastKey
        val startedPlaying = playing && !lastPlaying
        lastKey = key
        lastPlaying = playing

        val event = buildEvent(best, meta, key, title, artist, playing, state)
        if (playing && (trackChanged || startedPlaying) && !silent) {
            controller.post(event)
        } else {
            controller.updateIfShowing(event) // paused / state tweaks: refresh only if already visible
        }
    }

    private fun buildEvent(
        c: MediaController,
        meta: MediaMetadata,
        key: String,
        title: String,
        artist: String?,
        playing: Boolean,
        state: PlaybackState?,
    ): IslandEvent {
        val actions = state?.actions ?: 0L
        fun has(flag: Long) = (actions and flag) != 0L
        val tc = c.transportControls
        val media = MediaControlsState(
            isPlaying = playing,
            onPrevious = action(has(PlaybackState.ACTION_SKIP_TO_PREVIOUS)) { tc.skipToPrevious() },
            onPlayPause = if (playing) {
                action(has(PlaybackState.ACTION_PAUSE) || has(PlaybackState.ACTION_PLAY_PAUSE)) { tc.pause() }
            } else {
                action(has(PlaybackState.ACTION_PLAY) || has(PlaybackState.ACTION_PLAY_PAUSE)) { tc.play() }
            },
            onNext = action(has(PlaybackState.ACTION_SKIP_TO_NEXT)) { tc.skipToNext() },
        )
        return IslandEvent(
            id = ID,
            type = IslandEventType.Music,
            icon = if (playing) "🎵" else "⏸",
            compactText = title,
            title = title,
            subtitle = artist,
            media = media,
            artwork = artworkFor(key, meta),
        )
    }

    private fun action(enabled: Boolean, block: () -> Unit): (() -> Unit)? = if (enabled) block else null

    private fun artworkFor(key: String, meta: MediaMetadata): Bitmap? {
        if (key == cachedArtKey) return cachedArt
        val raw = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        cachedArt = downscale(raw)
        cachedArtKey = key
        return cachedArt
    }

    private fun downscale(b: Bitmap?): Bitmap? {
        if (b == null) return null
        val side = maxOf(b.width, b.height)
        if (side <= MAX_ART_PX) return b
        val f = MAX_ART_PX.toFloat() / side
        return try {
            Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
        } catch (e: Exception) {
            b
        }
    }

    private companion object {
        const val ID = "music"
        const val MAX_ART_PX = 256
    }
}
