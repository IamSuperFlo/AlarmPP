package com.example.photoalarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.photoalarm.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AlarmAdapter

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AlarmAdapter(emptyList(), ::onToggle, ::onClickAlarm)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AlarmEditActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ID, -1))
        }

        applyTitleGradient()
        askRuntimePermissions()
    }

    /** Paints the "Wuop" title with an RGB neon gradient. */
    private fun applyTitleGradient() {
        binding.txtTitle.post {
            val width = binding.txtTitle.paint.measureText(binding.txtTitle.text.toString())
            if (width <= 0f) return@post
            binding.txtTitle.paint.shader = LinearGradient(
                0f, 0f, width, 0f,
                intArrayOf(
                    Color.parseColor("#FF2D95"),
                    Color.parseColor("#B14CFF"),
                    Color.parseColor("#00E5FF"),
                    Color.parseColor("#39FF14")
                ),
                null,
                Shader.TileMode.CLAMP
            )
            binding.txtTitle.invalidate()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val all = AlarmStore.getAll(this)
        adapter.update(all)
        binding.emptyState.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onClickAlarm(alarm: Alarm) {
        startActivity(Intent(this, AlarmEditActivity::class.java)
            .putExtra(AlarmScheduler.EXTRA_ID, alarm.id))
    }

    private fun onToggle(alarm: Alarm, enabled: Boolean) {
        if (enabled && !Permissions.ensureExactAlarmAllowed(this)) {
            // Revert visually until permission is granted.
            refresh()
            return
        }
        alarm.enabled = enabled
        AlarmStore.put(this, alarm)
        if (enabled) {
            Permissions.ensureFullScreenIntentAllowed(this)
            AlarmScheduler.schedule(this, alarm)
        } else {
            AlarmScheduler.cancel(this, alarm.id)
        }
        refresh()
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
}
