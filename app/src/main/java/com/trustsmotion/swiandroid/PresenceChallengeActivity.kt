package com.trustsmotion.swiandroid

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.hypot

class PresenceChallengeActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: ChallengeOverlay
    private lateinit var status: TextView

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var detector: FaceDetector

    private var cameraProvider: ProcessCameraProvider? = null
    private var step = 0
    private var holdStartedAt: Long? = null
    private var completed = false

    private val sequence = (0..3).shuffled()

    private val targets = listOf(
        Pair(0.27f, 0.37f),
        Pair(0.73f, 0.37f),
        Pair(0.27f, 0.69f),
        Pair(0.73f, 0.69f)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()

        detector = FaceDetection.getClient(options)

        buildScreen()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 20, 25))
            setPadding(12, 18, 12, 12)
        }

        val heading = TextView(this).apply {
            text = "SWI HUMAN PRESENCE CHECK"
            textSize = 20f
            setTextColor(Color.rgb(242, 140, 40))
            setPadding(8, 8, 8, 12)
        }

        status = TextView(this).apply {
            text = "Allow camera access to begin."
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(8, 8, 8, 12)
        }

        val frame = FrameLayout(this)

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }

        overlay = ChallengeOverlay(this)

        frame.addView(
            previewView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        frame.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val instructions = TextView(this).apply {
            text = "Move your nose into the highlighted circle. Hold alignment for 3 seconds. Complete all four targets."
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(8, 14, 8, 12)
        }

        root.addView(heading)
        root.addView(status)
        root.addView(
            frame,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        root.addView(instructions)

        setContentView(root)
        window.decorView.keepScreenOn = true
    }

    private fun startCamera() {
        status.text = "Starting front camera..."

        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image

                    if (mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val input = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    detector.process(input)
                        .addOnSuccessListener { faces ->
                            val face = faces.firstOrNull()
                            val nose = face?.getLandmark(FaceLandmark.NOSE_BASE)

                            if (nose == null) {
                                runOnUiThread { onNoFace() }
                            } else {
                                val rotation = imageProxy.imageInfo.rotationDegrees
                                val rotatedWidth =
                                    if (rotation == 90 || rotation == 270) {
                                        imageProxy.height.toFloat()
                                    } else {
                                        imageProxy.width.toFloat()
                                    }

                                val rotatedHeight =
                                    if (rotation == 90 || rotation == 270) {
                                        imageProxy.width.toFloat()
                                    } else {
                                        imageProxy.height.toFloat()
                                    }

                                // Front camera preview is mirrored.
                                val x = 1f - (nose.position.x / rotatedWidth)
                                val y = nose.position.y / rotatedHeight

                                runOnUiThread {
                                    onFacePosition(x, y)
                                }
                            }
                        }
                        .addOnFailureListener {
                            runOnUiThread { onNoFace() }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )

                status.text = "Camera ready. Find the first target."
            } catch (e: Exception) {
                status.text = "CAMERA START FAILED: ${e.message}"
                setResult(RESULT_CANCELED)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onNoFace() {
        if (completed) return

        holdStartedAt = null
        overlay.update(sequence[step], 0L, false)
        status.text = "Face landmark not detected. Reposition and try again."
    }

    private fun onFacePosition(x: Float, y: Float) {
        if (completed || step >= sequence.size) return

        val targetIndex = sequence[step]
        val target = targets[targetIndex]

        val distance = hypot(x - target.first, y - target.second)
        val aligned = distance <= ALIGNMENT_RADIUS

        if (!aligned) {
            holdStartedAt = null
            overlay.update(targetIndex, 0L, false)
            status.text = "Move toward target ${step + 1} of 4."
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (holdStartedAt == null) {
            holdStartedAt = now
        }

        val elapsed = now - (holdStartedAt ?: now)
        val remaining = (HOLD_DURATION_MS - elapsed).coerceAtLeast(0L)

        overlay.update(targetIndex, remaining, true)
        status.text = "Target ${step + 1} of 4 — hold steady: ${remaining / 1000 + if (remaining % 1000 > 0) 1 else 0}s"

        if (elapsed >= HOLD_DURATION_MS) {
            step++
            holdStartedAt = null

            if (step >= sequence.size) {
                finishChallenge()
            } else {
                overlay.update(sequence[step], HOLD_DURATION_MS, false)
                status.text = "Target ${step + 1} of 4. Follow the arrow."
            }
        }
    }

    private fun finishChallenge() {
        if (completed) return
        completed = true

        cameraProvider?.unbindAll()
        status.text = "PRESENCE CHALLENGE PASSED"

        setResult(
            RESULT_OK,
            android.content.Intent().putExtra(
                "presence_challenge_passed",
                true
            )
        )

        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == CAMERA_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            status.text = "CAMERA PERMISSION DENIED. CHALLENGE HELD."
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()

        if (::detector.isInitialized) {
            detector.close()
        }

        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }

        super.onDestroy()
    }

    private class ChallengeOverlay(context: android.content.Context) :
        View(context) {

        private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var activeTarget = 0
        private var remainingMs = 3000L
        private var aligned = false

        init {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        fun update(target: Int, remaining: Long, isAligned: Boolean) {
            activeTarget = target
            remainingMs = remaining
            aligned = isAligned
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val centers = listOf(
                Pair(width * 0.27f, height * 0.37f),
                Pair(width * 0.73f, height * 0.37f),
                Pair(width * 0.27f, height * 0.69f),
                Pair(width * 0.73f, height * 0.69f)
            )

            val radius = minOf(width, height) * 0.075f
            val centerX = width * 0.5f
            val centerY = height * 0.52f

            linePaint.color = Color.rgb(242, 140, 40)
            linePaint.strokeWidth = 6f
            linePaint.style = Paint.Style.STROKE

            val target = centers[activeTarget]
            canvas.drawLine(
                centerX,
                centerY,
                target.first,
                target.second,
                linePaint
            )

            val angle = kotlin.math.atan2(
                (target.second - centerY).toDouble(),
                (target.first - centerX).toDouble()
            )

            val head = Path()
            val arrowSize = 22f
            head.moveTo(target.first, target.second)
            head.lineTo(
                target.first - arrowSize * kotlin.math.cos(angle - 0.5).toFloat(),
                target.second - arrowSize * kotlin.math.sin(angle - 0.5).toFloat()
            )
            head.lineTo(
                target.first - arrowSize * kotlin.math.cos(angle + 0.5).toFloat(),
                target.second - arrowSize * kotlin.math.sin(angle + 0.5).toFloat()
            )
            head.close()

            circlePaint.color = Color.rgb(242, 140, 40)
            circlePaint.style = Paint.Style.FILL
            canvas.drawPath(head, circlePaint)

            centers.forEachIndexed { index, point ->
                circlePaint.color = when {
                    index == activeTarget && aligned ->
                        Color.argb(90, 40, 220, 100)
                    index == activeTarget ->
                        Color.argb(90, 242, 140, 40)
                    else ->
                        Color.argb(45, 255, 255, 255)
                }

                canvas.drawCircle(
                    point.first,
                    point.second,
                    radius,
                    circlePaint
                )

                circlePaint.color = Color.WHITE
                circlePaint.style = Paint.Style.STROKE
                circlePaint.strokeWidth = 4f
                canvas.drawCircle(
                    point.first,
                    point.second,
                    radius,
                    circlePaint
                )
                circlePaint.style = Paint.Style.FILL

                textPaint.color = Color.WHITE
                textPaint.textSize = 30f
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(
                    (index + 1).toString(),
                    point.first,
                    point.second + 10f,
                    textPaint
                )
            }

            if (aligned) {
                textPaint.color = Color.WHITE
                textPaint.textSize = 26f
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(
                    "HOLD ${remainingMs / 1000 + if (remainingMs % 1000 > 0) 1 else 0}",
                    centerX,
                    height * 0.9f,
                    textPaint
                )
            }
        }
    }

    companion object {
        private const val CAMERA_REQUEST = 7201
        private const val HOLD_DURATION_MS = 3000L
        private const val ALIGNMENT_RADIUS = 0.15f
    }
}
