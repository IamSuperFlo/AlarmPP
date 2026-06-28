package com.example.photoalarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.photoalarm.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result handled lazily; we just ask */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.timePicker.setIs24HourView(true)

        askRuntimePermissions()

        binding.btnSet.setOnClickListener {
            if (!ensureExactAlarmAllowed()) return@setOnClickListener
            ensureFullScreenIntentAllowed()
            val hour = binding.timePicker.hour
            val minute = binding.timePicker.minute
            val daily = binding.switchDaily.isChecked
            val whenMillis = AlarmScheduler.setAlarm(this, hour, minute, daily)
            updateStatus(whenMillis, daily)
            Toast.makeText(this, "Alarmă setată.", Toast.LENGTH_SHORT).show()
        }

        binding.btnCancel.setOnClickListener {
            AlarmScheduler.cancel(this)
            binding.txtStatus.text = "Nicio alarmă setată."
            Toast.makeText(this, "Alarmă anulată.", Toast.LENGTH_SHORT).show()
        }

        binding.btnTest.setOnClickListener {
            // Start the ringing service right now to test the whole flow.
            ContextCompat.startForegroundService(this, Intent(this, AlarmService::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (AlarmScheduler.isEnabled(this)) {
            val whenMillis = AlarmScheduler.reschedule(this)
            if (whenMillis > 0) updateStatus(whenMillis, AlarmScheduler.isDaily(this))
        }
    }

    private fun updateStatus(whenMillis: Long, daily: Boolean) {
        val fmt = SimpleDateFormat("EEE dd MMM, HH:mm", Locale("ro"))
        val repeat = if (daily) " (zilnic)" else ""
        binding.txtStatus.text = "Următoarea alarmă: ${fmt.format(Date(whenMillis))}$repeat"
    }

    private fun askRuntimePermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestPermissions.launch(missing.toTypedArray())
    }

    /** On Android 12+ exact alarms may require user approval. */
    private fun ensureExactAlarmAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                Toast.makeText(
                    this,
                    "Permite „Alarme și mementouri” pentru această aplicație, apoi setează din nou.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:$packageName"))
                )
                return false
            }
        }
        return true
    }

    /** On Android 14+ the full-screen alarm may require the full-screen-intent permission. */
    private fun ensureFullScreenIntentAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                Toast.makeText(
                    this,
                    "Permite „Notificări pe tot ecranul” ca alarma să apară peste ecranul blocat.",
                    Toast.LENGTH_LONG
                ).show()
                try {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                            .setData(Uri.parse("package:$packageName"))
                    )
                } catch (_: Exception) {
                }
            }
        }
    }
}
