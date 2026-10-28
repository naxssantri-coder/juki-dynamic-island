package com.dynisland.app.domain

import com.dynisland.app.data.IslandSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single source of truth for what the island is showing.
 * Owns the priority queue and the compact auto-hide timeout.
 * Call from the main thread only.
 *
 * [canShow] lets the platform layer veto events (e.g. screen is off).
 */
class IslandController(
    private val scope: CoroutineScope,
    private val settings: StateFlow<IslandSettings>,
    private val policy: EventPriorityPolicy,
    private val canShow: (IslandEvent) -> Boolean = { true },
) {
    private val _state = MutableStateFlow(IslandUiState())
    val state: StateFlow<IslandUiState> = _state.asStateFlow()

    private val queue = mutableListOf<IslandEvent>()
    private var timeoutJob: Job? = null

    init {
        scope.launch {
            settings.map { it.islandEnabled }.distinctUntilChanged().collect { enabled ->
                if (!enabled) reset()
            }
        }
    }

    /** Show [event], or update it in place if an event with the same id is already on screen. */
    fun post(event: IslandEvent) {
        if (!settings.value.islandEnabled || !canShow(event)) return
        val current = _state.value
        val currentEvent = current.event

        if (current.mode == IslandMode.Hidden || currentEvent == null) {
            present(event)
            return
        }
        if (currentEvent.id == event.id) {
            _state.value = current.copy(event = event)
            if (current.mode == IslandMode.Compact) scheduleTimeout(event)
            return
        }
        val moreImportant = policy.priorityOf(event.type) > policy.priorityOf(currentEvent.type)
        val mayInterrupt = current.mode == IslandMode.Compact || policy.interruptsExpanded(event.type)
        if (moreImportant && mayInterrupt) {
            enqueue(currentEvent)
            present(event)
        } else {
            // Never yank an expanded island away from the user for something minor; wait in line.
            enqueue(event)
        }
    }

    /** Refresh an event's content only if it is already visible or queued. Never re-shows the island. */
    fun updateIfShowing(event: IslandEvent) {
        val s = _state.value
        if (s.event?.id == event.id && s.mode != IslandMode.Hidden) {
            _state.value = s.copy(event = event)
            return
        }
        val i = queue.indexOfFirst { it.id == event.id }
        if (i >= 0) queue[i] = event
    }

    /** The event is no longer relevant (music stopped, unplugged, timer cancelled...). */
    fun remove(id: String) {
        queue.removeAll { it.id == id }
        val s = _state.value
        if (s.event?.id == id && s.mode != IslandMode.Hidden) dismiss()
    }

    fun expand() {
        val s = _state.value
        val event = s.event ?: return
        if (s.mode != IslandMode.Compact || !event.expandable) return
        timeoutJob?.cancel()
        _state.value = s.copy(mode = IslandMode.Expanded)
    }

    fun collapse() {
        val s = _state.value
        if (s.mode != IslandMode.Expanded) return
        _state.value = s.copy(mode = IslandMode.Compact)
        s.event?.let { scheduleTimeout(it) }
    }

    fun dismiss() {
        timeoutJob?.cancel()
        val next = if (queue.isEmpty()) null else queue.removeAt(0)
        if (next != null) {
            present(next)
        } else {
            _state.update { it.copy(mode = IslandMode.Hidden) }
        }
    }

    fun reset() {
        timeoutJob?.cancel()
        queue.clear()
        _state.value = IslandUiState()
    }

    private fun present(event: IslandEvent) {
        _state.value = IslandUiState(mode = IslandMode.Compact, event = event)
        scheduleTimeout(event)
    }

    private fun enqueue(event: IslandEvent) {
        queue.removeAll { it.id == event.id }
        queue.add(event)
        queue.sortByDescending { policy.priorityOf(it.type) } // stable sort keeps arrival order
    }

    private fun scheduleTimeout(event: IslandEvent) {
        timeoutJob?.cancel()
        if (event.sticky) return
        val duration = event.durationMs ?: settings.value.compactDuration.millis
        timeoutJob = scope.launch {
            delay(duration)
            dismiss()
        }
    }
}
