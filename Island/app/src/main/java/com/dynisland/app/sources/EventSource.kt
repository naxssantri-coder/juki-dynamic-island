package com.dynisland.app.sources

import android.util.Log
import com.dynisland.app.data.IslandSettings
import kotlinx.coroutines.flow.StateFlow

/** Something that listens to the system and turns changes into island events. start/stop must be cheap and idempotent. */
interface EventSource {
    fun start()
    fun stop()
}

/**
 * Starts a source only while it is wanted (island on + feature toggle on) AND permitted.
 * Anything that is not wanted holds no receivers/listeners, so it costs nothing.
 */
class SourceManager(
    private val settings: StateFlow<IslandSettings>,
    private val entries: List<Entry>,
) {
    class Entry(
        val name: String,
        val source: EventSource,
        val wanted: (IslandSettings) -> Boolean,
        val permitted: () -> Boolean,
    )

    private val running = mutableSetOf<String>()

    fun refresh() {
        val s = settings.value
        for (e in entries) {
            val should = s.islandEnabled && e.wanted(s) && e.permitted()
            val isRunning = e.name in running
            if (should && !isRunning) {
                try {
                    e.source.start()
                    running += e.name
                } catch (ex: Exception) {
                    // SecurityException etc.: degrade silently, retried on next refresh().
                    Log.w(TAG, "Source ${e.name} could not start", ex)
                    runCatching { e.source.stop() }
                }
            } else if (!should && isRunning) {
                runCatching { e.source.stop() }
                running -= e.name
            }
        }
    }

    fun stopAll() {
        for (e in entries) if (e.name in running) runCatching { e.source.stop() }
        running.clear()
    }

    private companion object {
        const val TAG = "SourceManager"
    }
}
