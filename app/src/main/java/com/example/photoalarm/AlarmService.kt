package com.example.photoalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * Foreground service that rings the alarm. Supports a custom ringtone, gradual
 * volume ramp and optional vibration. Keeps ringing until the user photographs
 * the requested object(s) or snoozes.
 */
class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var volumeRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        val alarmId = intent?.getIntExtra(AlarmScheduler.EXTRA_ID, -1) ?: -1
        val snoozeCount = intent?.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0) ?: 0
        val alarm = if (alarmId >= 0) AlarmStore.get(this, alarmId) else null

        startForeground(NOTIF_ID, buildNotification(alarmId, snoozeCount, alarm?.label ?: ""))
        startRinging(alarm)
        return START_STICKY
    }

    private fun buildNotification(alarmId: Int, snoozeCount: Int, label: String): android.app.Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Alarmă", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarma cu poză"
                setBypassDnd(true)
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(this, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(AlarmScheduler.EXTRA_ID, alarmId)
            .putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, snoozeCount)
        val fullScreenPending = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (label.isBlank()) "Alarmă!" else "Alarmă — $label"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(title)
            .setContentText("Fă poză la obiectul cerut ca să oprești.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(fullScreenPending)
            .build()
    }

    private fun startRinging(alarm: Alarm?) {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)

        val gradual = alarm?.gradualVolume ?: true
        if (gradual) {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, (maxVol * 0.15f).toInt().coerceAtLeast(1), 0)
            var level = (maxVol * 0.15f).toInt().coerceAtLeast(1)
            volumeRunnable = object : Runnable {
                override fun run() {
                    level += (maxVol / 6).coerceAtLeast(1)
                    audio.setStreamVolume(AudioManager.STREAM_ALARM, level.coerceAtMost(maxVol), 0)
                    if (level < maxVol) handler.postDelayed(this, 3000)
                }
            }
            handler.postDelayed(volumeRunnable!!, 3000)
        } else {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
        }

        var uri: Uri? = alarm?.ringtoneUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
        if (uri == null) uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, uri!!)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
        }

        if (alarm?.vibrate != false) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 600, 600)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun stopEverything() {
        volumeRunnable?.let { handler.removeCallbacks(it) }
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEverything()
    }

    companion object {
        const val CHANNEL_ID = "alarm_channel"
        const val NOTIF_ID = 42
        const val ACTION_STOP = "com.example.photoalarm.STOP"

        fun stop(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP))
        }
    }
}
