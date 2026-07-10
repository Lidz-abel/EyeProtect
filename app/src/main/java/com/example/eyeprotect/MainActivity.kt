package com.example.eyeprotect

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.eyeprotect.camera.FrontCameraController
import com.example.eyeprotect.ui.EyeDetectionOverlay
import com.example.eyeprotect.usage.ScreenUsageRepository
import com.example.eyeprotect.usage.UsageAccessHelper
import com.example.eyeprotect.vision.EyeFrameResult
import com.example.eyeprotect.vision.FaceEyeDetection
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var usageRepository: ScreenUsageRepository
    private lateinit var frontCameraController: FrontCameraController
    private lateinit var statusText: TextView
    private lateinit var usageText: TextView
    private lateinit var eyeText: TextView
    private lateinit var leftEyeRoiView: ImageView
    private lateinit var rightEyeRoiView: ImageView
    private lateinit var previewView: PreviewView
    private lateinit var eyeOverlay: EyeDetectionOverlay
    private lateinit var previewContainer: FrameLayout
    private var lastEyeLogMillis = 0L

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startFrontCamera()
        } else {
            statusText.text = "Camera permission denied."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usageRepository = ScreenUsageRepository(this)
        frontCameraController = FrontCameraController(this, this)

        setContentView(createContentView())
        refreshUsage()
    }

    override fun onResume() {
        super.onResume()
        if (::usageText.isInitialized) {
            refreshUsage()
        }
    }

    override fun onDestroy() {
        frontCameraController.stopPreview()
        super.onDestroy()
    }

    private fun createContentView(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).roundToInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.app_background))
        }

        statusText = TextView(this).apply {
            textSize = 18f
            text = "EyeProtect"
            setTextColor(0xFF111827.toInt())
        }
        root.addView(statusText)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, dp(12))
        }

        val usageSettingsButton = Button(this).apply {
            text = "Usage Access"
            setOnClickListener {
                startActivity(UsageAccessHelper.createUsageAccessSettingsIntent())
            }
        }
        val refreshUsageButton = Button(this).apply {
            text = "Refresh Usage"
            setOnClickListener { refreshUsage() }
        }
        val startCameraButton = Button(this).apply {
            text = "Start Front Camera"
            setOnClickListener { ensureCameraPermissionThenStart() }
        }

        buttonRow.addView(usageSettingsButton)
        buttonRow.addView(refreshUsageButton)
        buttonRow.addView(startCameraButton)
        root.addView(buttonRow)

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        previewContainer = FrameLayout(this).apply {
            setBackgroundColor(0xFF111827.toInt())
            addView(
                previewView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            eyeOverlay = EyeDetectionOverlay(this@MainActivity)
            addView(
                eyeOverlay,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        root.addView(
            previewContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(320)
            )
        )

        eyeText = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF111827.toInt())
            setPadding(0, dp(10), 0, dp(6))
            text = "Eye detection: not started."
        }
        root.addView(eyeText)

        val roiRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(10))
        }
        leftEyeRoiView = createRoiImageView()
        rightEyeRoiView = createRoiImageView()
        roiRow.addView(
            leftEyeRoiView,
            LinearLayout.LayoutParams(0, dp(96), 1f).apply {
                marginEnd = dp(6)
            }
        )
        roiRow.addView(
            rightEyeRoiView,
            LinearLayout.LayoutParams(0, dp(96), 1f).apply {
                marginStart = dp(6)
            }
        )
        root.addView(roiRow)

        usageText = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFF1F2937.toInt())
            setPadding(0, dp(16), 0, 0)
        }

        val scrollView = ScrollView(this).apply {
            addView(usageText)
        }
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        return root
    }

    private fun createRoiImageView(): ImageView {
        return ImageView(this).apply {
            setBackgroundColor(0xFF0F172A.toInt())
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }

    private fun refreshUsage() {
        if (!UsageAccessHelper.hasUsageAccess(this)) {
            statusText.text = "Usage access is required for screen-time data."
            usageText.text = "Open Usage Access, find EyeProtect, and allow usage access."
            return
        }

        val usageItems = usageRepository.getTodayUsage()
        statusText.text = "Today's screen usage: ${formatDuration(usageItems.sumOf { it.totalTimeInForegroundMillis })}"
        usageText.text = if (usageItems.isEmpty()) {
            "No usage data returned yet."
        } else {
            usageItems.joinToString(separator = "\n\n") { item ->
                "${item.appName}\n" +
                    "${item.packageName}\n" +
                    "Used: ${formatDuration(item.totalTimeInForegroundMillis)}\n" +
                    "Last used: ${formatTime(item.lastTimeUsedMillis)}"
            }
        }
    }

    private fun ensureCameraPermissionThenStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startFrontCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startFrontCamera() {
        frontCameraController.startPreview(
            previewView = previewView,
            onFrameResult = { result ->
                handleEyeFrameResult(result)
            },
            onReady = {
                statusText.text = "Front camera and eye detection are running."
            },
            onError = { throwable ->
                statusText.text = "Camera or eye detection failed: ${throwable.message}"
            }
        )
    }

    private fun handleEyeFrameResult(result: EyeFrameResult) {
        val firstDetection = result.detections.firstOrNull()
        if (firstDetection == null) {
            eyeOverlay.setDetections(
                detections = emptyList(),
                sourceWidth = result.sourceWidth.coerceAtLeast(1),
                sourceHeight = result.sourceHeight.coerceAtLeast(1),
                mirror = true
            )
            leftEyeRoiView.setImageDrawable(null)
            rightEyeRoiView.setImageDrawable(null)
            eyeText.text = String.format(
                Locale.US,
                "Eye detection: NO FACE  fps=%.1f  latency=%dms",
                result.fps,
                result.detectionLatencyMillis
            )
            return
        }

        eyeOverlay.setDetections(
            detections = result.detections,
            sourceWidth = result.sourceWidth,
            sourceHeight = result.sourceHeight,
            mirror = true
        )

        leftEyeRoiView.setImageBitmap(firstDetection.leftEye?.roiBitmap)
        rightEyeRoiView.setImageBitmap(firstDetection.rightEye?.roiBitmap)

        val text = buildEyeCoordinateText(result, firstDetection)
        eyeText.text = text

        val now = System.currentTimeMillis()
        if (now - lastEyeLogMillis >= 500L) {
            Log.d("EyeProtect", buildStructuredEyeResult(result, firstDetection))
            lastEyeLogMillis = now
        }
    }

    private fun buildEyeCoordinateText(
        result: EyeFrameResult,
        detection: FaceEyeDetection
    ): String {
        val leftEye = detection.leftEye
        val rightEye = detection.rightEye
        val left = leftEye?.let {
            val box = it.box
            val center = it.center
            "L box=(${box.x1.roundToInt()},${box.y1.roundToInt()})-(${box.x2.roundToInt()},${box.y2.roundToInt()}) " +
                "center=(${center.x.roundToInt()},${center.y.roundToInt()})"
        } ?: "L unavailable"
        val right = rightEye?.let {
            val box = it.box
            val center = it.center
            "R box=(${box.x1.roundToInt()},${box.y1.roundToInt()})-(${box.x2.roundToInt()},${box.y2.roundToInt()}) " +
                "center=(${center.x.roundToInt()},${center.y.roundToInt()})"
        } ?: "R unavailable"
        val face = detection.faceBox
        val multiFace = if (result.faceCount > 1) "  MULTI_FACE=${result.faceCount}" else ""
        return String.format(
            Locale.US,
            "Eye detection: face=%d%s  fps=%.1f  latency=%dms  faceBox=(%d,%d)-(%d,%d)\n%s\n%s",
            detection.faceId,
            multiFace,
            result.fps,
            result.detectionLatencyMillis,
            face.x1.roundToInt(),
            face.y1.roundToInt(),
            face.x2.roundToInt(),
            face.y2.roundToInt(),
            left,
            right
        )
    }

    private fun buildStructuredEyeResult(
        result: EyeFrameResult,
        detection: FaceEyeDetection
    ): String {
        val left = detection.leftEye
        val right = detection.rightEye
        return "{" +
            "\"timestamp\":${result.timestampMillis}," +
            "\"face_id\":${detection.faceId}," +
            "\"face_count\":${result.faceCount}," +
            "\"fps\":${String.format(Locale.US, "%.1f", result.fps)}," +
            "\"latency_ms\":${result.detectionLatencyMillis}," +
            "\"source_width\":${result.sourceWidth}," +
            "\"source_height\":${result.sourceHeight}," +
            "\"face_box\":${boxToJson(detection.faceBox)}," +
            "\"left_eye_box\":${left?.box?.let { boxToJson(it) } ?: "null"}," +
            "\"left_eye_center\":${left?.center?.let { pointToJson(it.x, it.y) } ?: "null"}," +
            "\"right_eye_box\":${right?.box?.let { boxToJson(it) } ?: "null"}," +
            "\"right_eye_center\":${right?.center?.let { pointToJson(it.x, it.y) } ?: "null"}" +
            "}"
    }

    private fun boxToJson(box: com.example.eyeprotect.vision.BoundingBox): String {
        return "{" +
            "\"x1\":${box.x1.roundToInt()}," +
            "\"y1\":${box.y1.roundToInt()}," +
            "\"x2\":${box.x2.roundToInt()}," +
            "\"y2\":${box.y2.roundToInt()}" +
            "}"
    }

    private fun pointToJson(x: Float, y: Float): String {
        return "{" +
            "\"x\":${x.roundToInt()}," +
            "\"y\":${y.roundToInt()}" +
            "}"
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes)
            minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, seconds)
            else -> String.format(Locale.US, "%ds", seconds)
        }
    }

    private fun formatTime(millis: Long): String {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(millis))
    }
}
