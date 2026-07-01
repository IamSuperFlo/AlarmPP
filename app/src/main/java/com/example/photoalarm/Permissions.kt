package com.example.photoalarm

import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/** Shared runtime-permission checks for exact alarms and full-screen intents. */
object Permissions {

    /** Returns true if exact alarms are allowed; otherwise sends the user to settings. */
    fun ensureExactAlarmAllowed(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                Toast.makeText(
                    activity,
                    "Permite „Alarme și mementouri”, apoi salvează din nou alarma.",
                    Toast.LENGTH_LONG
                ).show()
                try {
                    activity.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:${activity.packageName}"))
                    )
                } catch (_: Exception) {
                }
                return false
            }
        }
        return true
    }

    /** On Android 14+ nudges the user to allow full-screen notifications (non-blocking). */
    fun ensureFullScreenIntentAllowed(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                Toast.makeText(
                    activity,
                    "Permite „Notificări pe tot ecranul” ca alarma să apară peste ecranul blocat.",
                    Toast.LENGTH_LONG
                ).show()
                try {
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                            .setData(Uri.parse("package:${activity.packageName}"))
                    )
                } catch (_: Exception) {
                }
            }
        }
    }
}
