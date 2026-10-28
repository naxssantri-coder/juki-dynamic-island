package com.dynisland.app.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dynisland.app.IslandApp

/** Wakes the app when the timer deadline passes, even if the CPU was asleep. */
class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TimerManager.ACTION_TIMER_DONE) return
        (context.applicationContext as? IslandApp)?.timerManager?.finishIfDue()
    }
}
