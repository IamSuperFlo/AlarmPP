package com.example.photoalarm

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.photoalarm.databinding.ActivityAlarmBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Full-screen alarm. Shows over the lock screen, blocks the back button, and only
 * lets the user dismiss the alarm after photographing the requested object(s).
 * Supports difficulty (multiple objects) and snooze.
 */
class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private var alarm: Alarm? = null
    private var snoozeCount = 0
    private var targets: List<ObjectClassifier.Target> = emptyList()
    private var index = 0

    private var imageCapture: ImageCapture? = null
    private var classifier: ObjectClassifier? = null
    private var dismissed = false
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            binding.txtClock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            clockHandler.postDelayed(this, 10_000)
        }
    }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else binding.txtResult.text = "Aplicația are nevoie de cameră pentru a opri alarma."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWhenLockedAndTurnScreenOn()
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        snoozeCount = intent.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0)
        alarm = if (id >= 0) AlarmStore.get(this, id) else null

        binding.txtLabel.text = alarm?.label ?: ""
        clockRunnable.run()

        val count = alarm?.objectCount ?: 1
        targets = ObjectClassifier.randomTargets(count)
        showCurrentTarget()

        setupSnoozeButton()

        bgExecutor.execute {
            try {
                classifier = ObjectClassifier(applicationContext)
            } catch (e: Exception) {
                runOnUiThread { binding.txtResult.text = "Eroare la încărcarea modelului AI: ${e.message}" }
            }
        }

        binding.btnCapture.setOnClickListener { capture() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    this@AlarmActivity,
                    "Fă poză la ${currentTarget().displayRo} ca să oprești!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun currentTarget() = targets[index.coerceIn(0, targets.size - 1)]

    private fun showCurrentTarget() {
        binding.txtTarget.text = currentTarget().displayRo
        binding.txtProgress.text =
            if (targets.size > 1) "Obiect ${index + 1} din ${targets.size}" else ""
    }

    private fun setupSnoozeButton() {
        val a = alarm
        val canSnooze = a != null && a.snoozeEnabled && snoozeCount < a.maxSnoozes
        if (canSnooze) {
            val left = a!!.maxSnoozes - snoozeCount
            binding.btnSnooze.text = "Amână ${a.snoozeMinutes} min (mai ai $left)"
            binding.btnSnooze.setOnClickListener {
                AlarmScheduler.scheduleSnooze(this, a, snoozeCount + 1)
                Toast.makeText(this, "Amânat ${a.snoozeMinutes} minute.", Toast.LENGTH_SHORT).show()
                AlarmService.stop(this)
                finish()
            }
        } else {
            binding.btnSnooze.visibility = android.view.View.GONE
        }
    }

    private fun showWhenLockedAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (e: Exception) {
                binding.txtResult.text = "Camera nu a pornit: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capture() {
        val ic = imageCapture ?: run {
            Toast.makeText(this, "Camera nu e gata.", Toast.LENGTH_SHORT).show(); return
        }
        if (classifier == null) {
            binding.txtResult.text = "Modelul AI încă se încarcă, mai încearcă o dată..."
            return
        }
        binding.txtResult.text = "Se analizează poza..."
        ic.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    val bmp = image.toBitmap()
                    image.close()
                    analyze(rotate(bmp, rotation))
                }

                override fun onError(exception: ImageCaptureException) {
                    binding.txtResult.text = "Eroare la fotografiere: ${exception.message}"
                }
            }
        )
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    private fun analyze(bitmap: Bitmap) {
        val target = currentTarget()
        bgExecutor.execute {
            val result = try {
                classifier?.matches(bitmap, target)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                when {
                    result == null -> binding.txtResult.text = "Nu am putut analiza poza. Mai încearcă."
                    result.matched -> onTargetMatched()
                    else -> {
                        val pct = (result.bestScore * 100).toInt()
                        binding.txtResult.text =
                            "Nu pare ${target.displayRo}. Am văzut: ${result.bestLabel} ($pct%). Încearcă din nou!"
                    }
                }
            }
        }
    }

    private fun onTargetMatched() {
        if (index < targets.size - 1) {
            index++
            binding.txtResult.text = "Corect! Următorul obiect 👇"
            showCurrentTarget()
        } else {
            dismiss()
        }
    }

    private fun dismiss() {
        if (dismissed) return
        dismissed = true
        binding.txtResult.text = "Corect! Alarmă oprită. Trezirea! ☀"
        AlarmService.stop(this)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
        classifier?.close()
        bgExecutor.shutdown()
    }
}
