package com.example.photoalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-schedules a saved alarm after the device reboots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.reschedule(context)
        }
    }
}
