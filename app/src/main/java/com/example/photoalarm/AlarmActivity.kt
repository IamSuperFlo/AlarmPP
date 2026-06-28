package com.example.photoalarm

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
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
import java.util.concurrent.Executors

/**
 * Full-screen alarm. Shows over the lock screen, blocks the back button, and only
 * lets the user dismiss the alarm after photographing the requested object.
 */
class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private lateinit var target: ObjectClassifier.Target
    private var imageCapture: ImageCapture? = null
    private var classifier: ObjectClassifier? = null
    private var dismissed = false
    private val bgExecutor = Executors.newSingleThreadExecutor()

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

        target = ObjectClassifier.randomTarget()
        binding.txtTarget.text = target.displayRo

        // Load the TFLite model off the UI thread.
        bgExecutor.execute {
            try {
                classifier = ObjectClassifier(applicationContext)
            } catch (e: Exception) {
                runOnUiThread {
                    binding.txtResult.text = "Eroare la încărcarea modelului AI: ${e.message}"
                }
            }
        }

        binding.btnCapture.setOnClickListener { capture() }

        // Block the back button — the alarm cannot be dismissed this way.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@AlarmActivity, "Fă poză la ${target.displayRo} ca să oprești!", Toast.LENGTH_SHORT).show()
            }
        })

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
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
        val ic = imageCapture
        if (ic == null) {
            Toast.makeText(this, "Camera nu e gata.", Toast.LENGTH_SHORT).show()
            return
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
                    val bitmap = image.toBitmap()
                    image.close()
                    val rotated = rotate(bitmap, rotation)
                    analyze(rotated)
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
        bgExecutor.execute {
            val result = try {
                classifier?.matches(bitmap, target)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (result == null) {
                    binding.txtResult.text = "Nu am putut analiza poza. Mai încearcă."
                    return@runOnUiThread
                }
                if (result.matched) {
                    onSuccess()
                } else {
                    val pct = (result.bestScore * 100).toInt()
                    binding.txtResult.text =
                        "Nu pare ${target.displayRo}. Am văzut: ${result.bestLabel} ($pct%). Încearcă din nou!"
                }
            }
        }
    }

    private fun onSuccess() {
        if (dismissed) return
        dismissed = true
        binding.txtResult.text = "Corect! Alarmă oprită. Trezirea! ☀"
        AlarmService.stop(this)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        classifier?.close()
        bgExecutor.shutdown()
    }
}
