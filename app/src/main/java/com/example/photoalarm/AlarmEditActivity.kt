package com.example.photoalarm

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.photoalarm.databinding.ActivityAlarmEditBinding
import java.util.Calendar

class AlarmEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmEditBinding
    private lateinit var alarm: Alarm
    private var selectedRingtone: String? = null

    private val ringtonePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            selectedRingtone = uri?.toString()
            updateRingtoneButton()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        alarm = if (id >= 0) (AlarmStore.get(this, id) ?: AlarmStore.newAlarm(this))
        else AlarmStore.newAlarm(this)

        bindToUi()

        binding.btnRingtone.setOnClickListener { openRingtonePicker() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnTest.setOnClickListener { test() }
        binding.btnDelete.setOnClickListener { delete() }
        binding.btnDelete.visibility = if (id >= 0) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun bindToUi() {
        binding.timePicker.setIs24HourView(true)
        binding.timePicker.hour = alarm.hour
        binding.timePicker.minute = alarm.minute
        binding.editLabel.setText(alarm.label)

        binding.chipMon.isChecked = alarm.days.contains(Calendar.MONDAY)
        binding.chipTue.isChecked = alarm.days.contains(Calendar.TUESDAY)
        binding.chipWed.isChecked = alarm.days.contains(Calendar.WEDNESDAY)
        binding.chipThu.isChecked = alarm.days.contains(Calendar.THURSDAY)
        binding.chipFri.isChecked = alarm.days.contains(Calendar.FRIDAY)
        binding.chipSat.isChecked = alarm.days.contains(Calendar.SATURDAY)
        binding.chipSun.isChecked = alarm.days.contains(Calendar.SUNDAY)

        when (alarm.objectCount) {
            2 -> binding.chipObj2.isChecked = true
            3 -> binding.chipObj3.isChecked = true
            else -> binding.chipObj1.isChecked = true
        }

        binding.switchGradual.isChecked = alarm.gradualVolume
        binding.switchVibrate.isChecked = alarm.vibrate
        binding.switchSnooze.isChecked = alarm.snoozeEnabled

        when (alarm.snoozeMinutes) {
            10 -> binding.chipMin10.isChecked = true
            15 -> binding.chipMin15.isChecked = true
            else -> binding.chipMin5.isChecked = true
        }
        when (alarm.maxSnoozes) {
            1 -> binding.chipMax1.isChecked = true
            5 -> binding.chipMax5.isChecked = true
            else -> binding.chipMax3.isChecked = true
        }

        selectedRingtone = alarm.ringtoneUri
        updateRingtoneButton()
    }

    private fun collectFromUi() {
        alarm.hour = binding.timePicker.hour
        alarm.minute = binding.timePicker.minute
        alarm.label = binding.editLabel.text?.toString()?.trim() ?: ""

        val days = mutableSetOf<Int>()
        if (binding.chipMon.isChecked) days.add(Calendar.MONDAY)
        if (binding.chipTue.isChecked) days.add(Calendar.TUESDAY)
        if (binding.chipWed.isChecked) days.add(Calendar.WEDNESDAY)
        if (binding.chipThu.isChecked) days.add(Calendar.THURSDAY)
        if (binding.chipFri.isChecked) days.add(Calendar.FRIDAY)
        if (binding.chipSat.isChecked) days.add(Calendar.SATURDAY)
        if (binding.chipSun.isChecked) days.add(Calendar.SUNDAY)
        alarm.days = days

        alarm.objectCount = when {
            binding.chipObj3.isChecked -> 3
            binding.chipObj2.isChecked -> 2
            else -> 1
        }
        alarm.gradualVolume = binding.switchGradual.isChecked
        alarm.vibrate = binding.switchVibrate.isChecked
        alarm.snoozeEnabled = binding.switchSnooze.isChecked
        alarm.snoozeMinutes = when {
            binding.chipMin15.isChecked -> 15
            binding.chipMin10.isChecked -> 10
            else -> 5
        }
        alarm.maxSnoozes = when {
            binding.chipMax1.isChecked -> 1
            binding.chipMax5.isChecked -> 5
            else -> 3
        }
        alarm.ringtoneUri = selectedRingtone
    }

    private fun save() {
        collectFromUi()
        alarm.enabled = true
        AlarmStore.put(this, alarm)
        if (!Permissions.ensureExactAlarmAllowed(this)) {
            Toast.makeText(this, "Alarma a fost salvată. Acordă permisiunea și revino.", Toast.LENGTH_LONG).show()
            return
        }
        Permissions.ensureFullScreenIntentAllowed(this)
        val at = AlarmScheduler.schedule(this, alarm)
        Toast.makeText(this, "Alarmă salvată pentru ${timeUntil(at)}.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun test() {
        collectFromUi()
        AlarmStore.put(this, alarm)
        ContextCompat.startForegroundService(
            this,
            Intent(this, AlarmService::class.java)
                .putExtra(AlarmScheduler.EXTRA_ID, alarm.id)
                .putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0)
        )
    }

    private fun delete() {
        AlarmScheduler.cancel(this, alarm.id)
        AlarmStore.delete(this, alarm.id)
        Toast.makeText(this, "Alarmă ștearsă.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alege tonul alarmei")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                selectedRingtone?.let { Uri.parse(it) }
            )
        }
        try {
            ringtonePicker.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Nu am putut deschide lista de tonuri.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateRingtoneButton() {
        val name = selectedRingtone?.let {
            try {
                RingtoneManager.getRingtone(this, Uri.parse(it))?.getTitle(this)
            } catch (_: Exception) {
                null
            }
        } ?: "Implicit"
        binding.btnRingtone.text = "Ton: $name"
    }

    private fun timeUntil(at: Long): String {
        if (at <= 0) return "curând"
        val mins = ((at - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
        val h = mins / 60
        val m = mins % 60
        return if (h > 0) "$h h $m min" else "$m min"
    }
}
