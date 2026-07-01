package com.example.photoalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Fired by AlarmManager. Starts the ringing service and reschedules repeats. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getIntExtra(AlarmScheduler.EXTRA_ID, -1) ?: -1
        if (id < 0) return
        val snoozeCount = intent?.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0) ?: 0
        val alarm = AlarmStore.get(context, id) ?: return

        // Start ringing (foreground service allowed from an alarm-triggered receiver).
        val serviceIntent = Intent(context, AlarmService::class.java)
            .putExtra(AlarmScheduler.EXTRA_ID, id)
            .putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, snoozeCount)
        ContextCompat.startForegroundService(context, serviceIntent)

        // Only on the original fire (not on a snooze re-fire) handle repeat/one-shot.
        if (snoozeCount == 0) {
            if (alarm.days.isNotEmpty()) {
                AlarmScheduler.schedule(context, alarm) // next matching weekday
            } else {
                alarm.enabled = false
                AlarmStore.put(context, alarm)
            }
        }
    }
}
