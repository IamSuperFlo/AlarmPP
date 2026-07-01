package com.example.photoalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Schedules exact alarms per [Alarm], including day-of-week repeat and snooze. */
object AlarmScheduler {

    const val EXTRA_ID = "alarm_id"
    const val EXTRA_SNOOZE_COUNT = "snooze_count"
    const val ACTION_FIRE = "com.example.photoalarm.ALARM_FIRE"

    private fun operation(context: Context, alarmId: Int, snoozeCount: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_ID, alarmId)
            .putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
        return PendingIntent.getBroadcast(
            context, alarmId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showIntent(context: Context, alarmId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context, 900000 + alarmId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Schedules the next occurrence of [alarm]. Returns the trigger time (millis) or -1. */
    fun schedule(context: Context, alarm: Alarm): Long {
        if (!alarm.enabled) {
            cancel(context, alarm.id)
            return -1L
        }
        val triggerAt = nextTrigger(alarm)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent(context, alarm.id))
        am.setAlarmClock(info, operation(context, alarm.id, 0))
        return triggerAt
    }

    /** Schedules a snooze for [alarm], [snoozeMinutes] from now. */
    fun scheduleSnooze(context: Context, alarm: Alarm, snoozeCount: Int): Long {
        val triggerAt = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent(context, alarm.id))
        am.setAlarmClock(info, operation(context, alarm.id, snoozeCount))
        return triggerAt
    }

    fun cancel(context: Context, alarmId: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(operation(context, alarmId, 0))
    }

    /** Re-schedules every enabled alarm (used after reboot). */
    fun rescheduleAll(context: Context) {
        AlarmStore.getAll(context).filter { it.enabled }.forEach { schedule(context, it) }
    }

    fun nextTrigger(alarm: Alarm): Long {
        val now = Calendar.getInstance()
        val base = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (alarm.days.isEmpty()) {
            if (base.timeInMillis <= now.timeInMillis) base.add(Calendar.DAY_OF_YEAR, 1)
            return base.timeInMillis
        }
        for (i in 0..7) {
            val c = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            if (c.timeInMillis > now.timeInMillis && alarm.days.contains(c.get(Calendar.DAY_OF_WEEK))) {
                return c.timeInMillis
            }
        }
        return base.timeInMillis
    }
}
