package com.dynisland.app.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.dynisland.app.data.IslandSettings
import com.dynisland.app.domain.IslandAction
import com.dynisland.app.domain.IslandController
import com.dynisland.app.domain.IslandEvent
import com.dynisland.app.domain.IslandEventType
import com.dynisland.app.domain.IslandMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface TimerState {
    data object Idle : TimerState
    data class Running(val endsAtElapsed: Long, val endsAtWall: Long, val totalMs: Long) : TimerState
    data class Paused(val remainingMs: Long, val totalMs: Long) : TimerState
    data object Finished : TimerState
}

/**
 * Countdown timer. Time is tracked as an absolute elapsedRealtime() deadline, so it stays correct
 * no matter how the UI behaves. Completion is guaranteed by an AlarmManager alarm (works while the
 * CPU sleeps); the once-per-second tick only runs while the timer is actually visible on the island.
 * State lives in memory: if Android kills the app process, a running timer is lost.
 */
class TimerManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val controller: IslandController,
    private val settings: StateFlow<IslandSettings>,
) {
    private val _state = MutableStateFlow<TimerState>(TimerState.Idle)
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private var finishJob: Job? = null

    init {
        val timerVisible = controller.state
            .map { it.event?.id == RUNNING_ID && it.mode != IslandMode.Hidden }
            .distinctUntilChanged()
        scope.launch {
            combine(timerVisible, _state) { visible, st -> visible to st }
                .collectLatest { (visible, st) ->
                    if (!visible) return@collectLatest // nothing on screen: no ticking at all
                    when (st) {
                        is TimerState.Running -> tickWhileVisible(st)
                        is TimerState.Paused -> controller.updateIfShowing(pausedEvent(st.remainingMs))
                        else -> Unit
                    }
                }
        }
    }

    fun start(durationMs: Long) {
        if (!settings.value.timerEnabled || durationMs <= 0) return
        clearScheduling()
        val endsAt = SystemClock.elapsedRealtime() + durationMs
        _state.value = TimerState.Running(endsAt, System.currentTimeMillis() + durationMs, durationMs)
        schedule(endsAt)
        controller.remove(FINISHED_ID)
        controller.post(runningEvent(durationMs))
    }

    fun pause() {
        val st = _state.value as? TimerState.Running ?: return
        val remaining = (st.endsAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        clearScheduling()
        _state.value = TimerState.Paused(remaining, st.totalMs)
        controller.updateIfShowing(pausedEvent(remaining))
    }

    fun resume() {
        val st = _state.value as? TimerState.Paused ?: return
        val endsAt = SystemClock.elapsedRealtime() + st.remainingMs
        _state.value = TimerState.Running(endsAt, System.currentTimeMillis() + st.remainingMs, st.totalMs)
        schedule(endsAt)
        controller.updateIfShowing(runningEvent(st.remainingMs))
    }

    fun stop() {
        clearScheduling()
        _state.value = TimerState.Idle
        controller.remove(RUNNING_ID)
        controller.remove(FINISHED_ID)
    }

    /** Called by the alarm receiver and by the in-process job. Safe to call spuriously. */
    fun finishIfDue() {
        val st = _state.value as? TimerState.Running ?: return
        if (SystemClock.elapsedRealtime() + 300 < st.endsAtElapsed) return
        clearScheduling()
        _state.value = TimerState.Finished
        controller.remove(RUNNING_ID)
        vibrate()
        controller.post(finishedEvent())
    }

    /** Timer finished while the screen was off: show it again once the screen is on. */
    fun onScreenOn() {
        if (_state.value is TimerState.Finished) controller.post(finishedEvent())
    }

    fun acknowledgeFinished() {
        if (_state.value is TimerState.Finished) _state.value = TimerState.Idle
        controller.remove(FINISHED_ID)
    }

    private suspend fun tickWhileVisible(st: TimerState.Running) {
        while (true) {
            val remaining = st.endsAtElapsed - SystemClock.elapsedRealtime()
            if (remaining <= 0) return
            controller.updateIfShowing(runningEvent(remaining))
            val untilNextSecond = remaining % 1000L
            delay(maxOf(if (untilNextSecond == 0L) 1000L else untilNextSecond, 50L))
        }
    }

    private fun schedule(endsAtElapsed: Long) {
        finishJob = scope.launch {
            delay((endsAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            finishIfDue()
        }
        try {
            val pi = alarmIntent()
            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endsAtElapsed, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endsAtElapsed, pi)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule timer alarm; relying on in-process timer", e)
        }
    }

    private fun clearScheduling() {
        finishJob?.cancel()
        finishJob = null
        runCatching { alarmManager.cancel(alarmIntent()) }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TimerAlarmReceiver::class.java).setAction(ACTION_TIMER_DONE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @Suppress("DEPRECATION")
    private fun vibrate() {
        try {
            val vibrator = context.getSystemService(Vibrator::class.java) ?: return
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1))
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    private fun runningEvent(remainingMs: Long): IslandEvent {
        val text = format(remainingMs)
        return IslandEvent(
            id = RUNNING_ID,
            type = IslandEventType.Timer,
            icon = "⏱",
            compactText = text,
            title = "Timer",
            bigText = text,
            actions = listOf(IslandAction("Pause") { pause() }, IslandAction("Stop") { stop() }),
        )
    }

    private fun pausedEvent(remainingMs: Long): IslandEvent {
        val text = format(remainingMs)
        return IslandEvent(
            id = RUNNING_ID,
            type = IslandEventType.Timer,
            icon = "⏸",
            compactText = text,
            title = "Timer paused",
            bigText = text,
            actions = listOf(IslandAction("Resume") { resume() }, IslandAction("Stop") { stop() }),
        )
    }

    private fun finishedEvent() = IslandEvent(
        id = FINISHED_ID,
        type = IslandEventType.Timer,
        icon = "🔔",
        compactText = "Timer Finished",
        title = "Timer Finished",
        subtitle = "Time's up",
        actions = listOf(IslandAction("Dismiss") { acknowledgeFinished() }),
        durationMs = FINISHED_VISIBLE_MS,
    )

    companion object {
        private const val TAG = "TimerManager"
        const val ACTION_TIMER_DONE = "com.dynisland.app.action.TIMER_DONE"
        private const val RUNNING_ID = "timer"
        private const val FINISHED_ID = "timer-finished"
        private const val FINISHED_VISIBLE_MS = 10_000L

        fun format(ms: Long): String {
            val totalSeconds = (ms.coerceAtLeast(0) + 999) / 1000 // round up so 0:00 only shows when truly done
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        }
    }
}
