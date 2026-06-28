package com.example.photoalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Schedules and cancels the exact alarm using AlarmManager.
 * Alarm state (hour/minute/daily/enabled) is persisted in SharedPreferences
 * so it can be re-scheduled after a reboot.
 */
object AlarmScheduler {

    const val PREFS = "photo_alarm_prefs"
    const val KEY_HOUR = "hour"
    const val KEY_MINUTE = "minute"
    const val KEY_DAILY = "daily"
    const val KEY_ENABLED = "enabled"

    const val ACTION_FIRE = "com.example.photoalarm.ALARM_FIRE"
    private const val REQUEST_CODE = 1001

    private fun operationIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Saves the alarm and schedules the next trigger. Returns the time it will fire (millis). */
    fun setAlarm(context: Context, hour: Int, minute: Int, daily: Boolean): Long {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .putBoolean(KEY_DAILY, daily)
            .putBoolean(KEY_ENABLED, true)
            .apply()
        return schedule(context, hour, minute)
    }

    /** Re-schedules from saved prefs (used after reboot or after the alarm fires when daily). */
    fun reschedule(context: Context): Long {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean(KEY_ENABLED, false)) return -1L
        val hour = p.getInt(KEY_HOUR, 7)
        val minute = p.getInt(KEY_MINUTE, 0)
        return schedule(context, hour, minute)
    }

    private fun schedule(context: Context, hour: Int, minute: Int): Long {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (next.timeInMillis <= now.timeInMillis) {
            next.add(Calendar.DAY_OF_YEAR, 1)
        }
        val info = AlarmManager.AlarmClockInfo(next.timeInMillis, showIntent(context))
        am.setAlarmClock(info, operationIntent(context))
        return next.timeInMillis
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(operationIntent(context))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, false)
            .apply()
    }

    fun isDaily(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DAILY, false)

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
}
