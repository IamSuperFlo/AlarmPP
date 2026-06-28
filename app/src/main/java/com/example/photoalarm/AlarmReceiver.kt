package com.example.photoalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Triggered by AlarmManager at the scheduled time. Starts the foreground
 * AlarmService (which rings + shows the full-screen alarm). Alarm-triggered
 * receivers are allowed to start a foreground service from the background.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Start the ringing service.
        val serviceIntent = Intent(context, AlarmService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)

        // If this is a daily alarm, schedule the next day's occurrence.
        if (AlarmScheduler.isDaily(context) && AlarmScheduler.isEnabled(context)) {
            AlarmScheduler.reschedule(context)
        } else {
            // One-shot alarm: mark as disabled after firing.
            context.getSharedPreferences(AlarmScheduler.PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(AlarmScheduler.KEY_ENABLED, false).apply()
        }
    }
}
