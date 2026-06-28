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
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * Foreground service that rings the alarm (loud looping sound + vibration) and
 * shows the full-screen AlarmActivity. Keeps ringing until the user proves they
 * photographed the requested object (AlarmActivity calls stop()).
 */
class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification())
        startRinging()
        return START_STICKY
    }

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarmă",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarma cu poză"
                setBypassDnd(true)
            }
            nm.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(this, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val fullScreenPending = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle("Alarmă!")
            .setContentText("Fă poză la obiectul cerut ca să oprești.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(fullScreenPending)
            .build()
    }

    private fun startRinging() {
        // Force the alarm stream to max volume.
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )

        var uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
        if (uri == null) {
            uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
        }

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

        // Vibration loop.
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
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

    private fun stopEverything() {
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

        /** Called by AlarmActivity once the correct object has been photographed. */
        fun stop(context: Context) {
            val intent = Intent(context, AlarmService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
