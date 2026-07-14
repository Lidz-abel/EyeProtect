package com.example.eyeprotect

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.eyeprotect.camera.AnalysisResolution
import com.example.eyeprotect.camera.FrontCameraController
import com.example.eyeprotect.ui.EyeDetectionOverlay
import com.example.eyeprotect.usage.ScreenUsageRepository
import com.example.eyeprotect.usage.UsageAccessHelper
import com.example.eyeprotect.vision.DistanceEstimate
import com.example.eyeprotect.vision.DistanceState
import com.example.eyeprotect.vision.EyeDistanceEstimator
import com.example.eyeprotect.vision.EyeFrameResult
import com.example.eyeprotect.vision.FaceEyeDetection
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    companion object {
        private const val KEY_RESUME_CAMERA = "resume_camera"
        private const val PREFS_NAME = "eyeprotect_settings"
        private const val PREF_CALIBRATED_EYE_DISTANCE_PX = "calibrated_eye_distance_px"
        private const val PREF_ANALYSIS_RESOLUTION = "analysis_resolution"
    }

    private lateinit var usageRepository: ScreenUsageRepository
    private lateinit var frontCameraController: FrontCameraController
    private lateinit var distanceEstimator: EyeDistanceEstimator
    private lateinit var settings: SharedPreferences
    private lateinit var statusText: TextView
    private lateinit var usageText: TextView
    private lateinit var eyeText: TextView
    private lateinit var distanceText: TextView
    private lateinit var previewView: PreviewView
    private lateinit var eyeOverlay: EyeDetectionOverlay
    private lateinit var previewContainer: FrameLayout
    private lateinit var startCameraButton: Button
    private lateinit var stopCameraButton: Button
    private lateinit var calibrateDistanceButton: Button
    private lateinit var resolutionText: TextView
    private lateinit var lowResolutionButton: Button
    private lateinit var balancedResolutionButton: Button
    private lateinit var highResolutionButton: Button
    private var lastEyeLogMillis = 0L
    private var cameraRunning = false
    private var resumeCameraOnForeground = false
    private var screenReceiverRegistered = false
    private var selectedAnalysisResolution = AnalysisResolution.BALANCED
    private var latestFrameResult: EyeFrameResult? = null
    private var latestFaceDetection: FaceEyeDetection? = null
    private var latestDistanceEstimate: DistanceEstimate? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                resumeCameraOnForeground = false
                stopCamera("Screen is off. Camera stopped.", clearDetection = true)
            }
        }
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startFrontCamera()
        } else {
            statusText.text = "Camera permission denied. Enable Camera permission in system settings."
            eyeText.text = "Eye detection: camera permission is required."
            updateCameraControls()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usageRepository = ScreenUsageRepository(this)
        frontCameraController = FrontCameraController(this, this)
        settings = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        selectedAnalysisResolution = AnalysisResolution.fromKey(
            settings.getString(PREF_ANALYSIS_RESOLUTION, AnalysisResolution.BALANCED.key)
        )
        distanceEstimator = EyeDistanceEstimator(
            calibratedEyeDistancePx = if (settings.contains(PREF_CALIBRATED_EYE_DISTANCE_PX)) {
                settings.getFloat(PREF_CALIBRATED_EYE_DISTANCE_PX, 0f)
            } else {
                null
            }
        )
        resumeCameraOnForeground = savedInstanceState?.getBoolean(KEY_RESUME_CAMERA, false) == true

        setContentView(createContentView())
        refreshUsage()
        updateCameraControls()
    }

    override fun onStart() {
        super.onStart()
        registerScreenOffReceiver()
    }

    override fun onResume() {
        super.onResume()
        if (::usageText.isInitialized) {
            refreshUsage()
        }
        if (resumeCameraOnForeground && hasCameraPermission()) {
            resumeCameraOnForeground = false
            startFrontCamera()
        }
    }

    override fun onPause() {
        if (cameraRunning) {
            resumeCameraOnForeground = true
            stopCamera("App moved to background. Camera released.", clearDetection = true)
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_RESUME_CAMERA, cameraRunning || resumeCameraOnForeground)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        unregisterScreenOffReceiver()
        super.onStop()
    }

    override fun onDestroy() {
        stopCamera("Camera stopped.", clearDetection = true)
        super.onDestroy()
    }

    private fun createContentView(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).roundToInt()
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.app_background))
        }

        statusText = TextView(this).apply {
            textSize = 18f
            text = "EyeProtect"
            setTextColor(0xFF111827.toInt())
        }
        root.addView(statusText)

        val buttonRow = LinearLayout(this).apply {
            orientation = if (isLandscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
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
        startCameraButton = Button(this).apply {
            text = "Start Front Camera"
            setOnClickListener { ensureCameraPermissionThenStart() }
        }
        stopCameraButton = Button(this).apply {
            text = "Stop Camera"
            setOnClickListener {
                resumeCameraOnForeground = false
                stopCamera("Camera stopped by user.", clearDetection = true)
            }
        }
        calibrateDistanceButton = Button(this).apply {
            text = "Calibrate 40cm"
            setOnClickListener { calibrateDistanceAt40cm() }
        }

        buttonRow.addView(usageSettingsButton)
        buttonRow.addView(refreshUsageButton)
        buttonRow.addView(startCameraButton)
        buttonRow.addView(stopCameraButton)
        buttonRow.addView(calibrateDistanceButton)
        root.addView(buttonRow)

        val resolutionRow = LinearLayout(this).apply {
            orientation = if (isLandscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        resolutionText = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF111827.toInt())
            text = buildResolutionText()
        }
        lowResolutionButton = Button(this).apply {
            text = "Low"
            setOnClickListener { selectAnalysisResolution(AnalysisResolution.LOW_POWER) }
        }
        balancedResolutionButton = Button(this).apply {
            text = "Balanced"
            setOnClickListener { selectAnalysisResolution(AnalysisResolution.BALANCED) }
        }
        highResolutionButton = Button(this).apply {
            text = "HD"
            setOnClickListener { selectAnalysisResolution(AnalysisResolution.HIGH_QUALITY) }
        }
        resolutionRow.addView(resolutionText)
        resolutionRow.addView(lowResolutionButton)
        resolutionRow.addView(balancedResolutionButton)
        resolutionRow.addView(highResolutionButton)
        root.addView(resolutionRow)

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
                if (isLandscape) dp(280) else dp(360)
            )
        )

        eyeText = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF111827.toInt())
            setPadding(0, dp(10), 0, dp(6))
            text = "Eye detection: not started."
        }
        root.addView(eyeText)

        distanceText = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF111827.toInt())
            setPadding(0, 0, 0, dp(8))
            text = buildInitialDistanceText()
        }
        root.addView(distanceText)

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
        if (hasCameraPermission()) {
            startFrontCamera()
        } else {
            statusText.text = "Camera permission is required for eye detection."
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startFrontCamera() {
        if (cameraRunning) {
            return
        }
        startCameraButton.isEnabled = false
        stopCameraButton.isEnabled = false
        statusText.text = "Starting front camera..."
        frontCameraController.startPreview(
            previewView = previewView,
            analysisResolution = selectedAnalysisResolution,
            onFrameResult = { result ->
                handleEyeFrameResult(result)
            },
            onReady = {
                cameraRunning = true
                updateCameraControls()
                statusText.text = "Front camera and eye detection are running."
            },
            onError = { throwable ->
                cameraRunning = false
                resumeCameraOnForeground = false
                updateCameraControls()
                statusText.text = "Camera or eye detection failed: ${throwable.message}"
            }
        )
    }

    private fun stopCamera(message: String, clearDetection: Boolean) {
        frontCameraController.stopPreview()
        cameraRunning = false
        updateCameraControls()
        if (::statusText.isInitialized) {
            statusText.text = message
        }
        if (clearDetection && ::eyeText.isInitialized) {
            clearDetectionUi(message)
        }
    }

    private fun clearDetectionUi(message: String) {
        if (::eyeOverlay.isInitialized) {
            eyeOverlay.setDetections(
                detections = emptyList(),
                sourceWidth = previewView.width.coerceAtLeast(1),
                sourceHeight = previewView.height.coerceAtLeast(1),
                mirror = true
            )
        }
        latestFrameResult = null
        latestFaceDetection = null
        latestDistanceEstimate = null
        eyeText.text = "Eye detection: $message"
        if (::distanceText.isInitialized) {
            distanceText.text = buildInitialDistanceText()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun updateCameraControls() {
        if (!::startCameraButton.isInitialized || !::stopCameraButton.isInitialized ||
            !::calibrateDistanceButton.isInitialized
        ) {
            return
        }
        startCameraButton.isEnabled = !cameraRunning
        stopCameraButton.isEnabled = cameraRunning
        calibrateDistanceButton.isEnabled = cameraRunning
        updateResolutionControls()
    }

    private fun updateResolutionControls() {
        if (!::lowResolutionButton.isInitialized || !::balancedResolutionButton.isInitialized ||
            !::highResolutionButton.isInitialized || !::resolutionText.isInitialized
        ) {
            return
        }

        resolutionText.text = buildResolutionText()
        lowResolutionButton.isEnabled = selectedAnalysisResolution != AnalysisResolution.LOW_POWER
        balancedResolutionButton.isEnabled = selectedAnalysisResolution != AnalysisResolution.BALANCED
        highResolutionButton.isEnabled = selectedAnalysisResolution != AnalysisResolution.HIGH_QUALITY
    }

    private fun selectAnalysisResolution(resolution: AnalysisResolution) {
        if (selectedAnalysisResolution == resolution) {
            return
        }

        selectedAnalysisResolution = resolution
        settings.edit()
            .putString(PREF_ANALYSIS_RESOLUTION, resolution.key)
            .apply()
        updateResolutionControls()

        if (cameraRunning) {
            statusText.text = "Switching analysis resolution to ${resolution.label}..."
            frontCameraController.stopPreview()
            cameraRunning = false
            clearDetectionUi("Resolution changed. Restarting camera...")
            startFrontCamera()
        } else {
            statusText.text = "Analysis resolution set to ${resolution.label}."
        }
    }

    private fun buildResolutionText(): String {
        return "Analysis: ${selectedAnalysisResolution.label}"
    }

    private fun registerScreenOffReceiver() {
        if (screenReceiverRegistered) {
            return
        }
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenReceiverRegistered = true
    }

    private fun unregisterScreenOffReceiver() {
        if (!screenReceiverRegistered) {
            return
        }
        unregisterReceiver(screenOffReceiver)
        screenReceiverRegistered = false
    }

    private fun handleEyeFrameResult(result: EyeFrameResult) {
        latestFrameResult = result
        val firstDetection = result.detections.firstOrNull()
        if (firstDetection == null) {
            latestFaceDetection = null
            latestDistanceEstimate = null
            eyeOverlay.setDetections(
                detections = emptyList(),
                sourceWidth = result.sourceWidth.coerceAtLeast(1),
                sourceHeight = result.sourceHeight.coerceAtLeast(1),
                mirror = true
            )
            eyeText.text = String.format(
                Locale.US,
                "Eye detection: NO FACE  fps=%.1f  latency=%dms",
                result.fps,
                result.detectionLatencyMillis
            )
            distanceText.text = "Distance: no face."
            return
        }
        latestFaceDetection = firstDetection

        eyeOverlay.setDetections(
            detections = result.detections,
            sourceWidth = result.sourceWidth,
            sourceHeight = result.sourceHeight,
            mirror = true
        )

        val distanceEstimate = estimateDistance(result, firstDetection)
        latestDistanceEstimate = distanceEstimate
        distanceText.text = buildDistanceText(distanceEstimate)

        val text = buildEyeCoordinateText(result, firstDetection, distanceEstimate)
        eyeText.text = text

        val now = System.currentTimeMillis()
        if (now - lastEyeLogMillis >= 500L) {
            Log.d("EyeProtect", buildStructuredEyeResult(result, firstDetection, distanceEstimate))
            lastEyeLogMillis = now
        }
    }

    private fun estimateDistance(
        result: EyeFrameResult,
        detection: FaceEyeDetection
    ): DistanceEstimate {
        return distanceEstimator.estimate(
            leftEyeCenter = detection.leftEye?.center,
            rightEyeCenter = detection.rightEye?.center,
            sourceWidth = result.sourceWidth,
            faceCount = result.faceCount
        )
    }

    private fun calibrateDistanceAt40cm() {
        val detection = latestFaceDetection
        if (detection == null) {
            distanceText.text = "Distance: calibration failed. No face detected."
            return
        }

        val eyeDistancePx = distanceEstimator.calculateEyeDistancePx(
            leftEyeCenter = detection.leftEye?.center,
            rightEyeCenter = detection.rightEye?.center
        )
        if (eyeDistancePx == null) {
            distanceText.text = "Distance: calibration failed. Both eyes are required."
            return
        }

        distanceEstimator.setCalibration(eyeDistancePx)
        settings.edit()
            .putFloat(PREF_CALIBRATED_EYE_DISTANCE_PX, eyeDistancePx)
            .apply()

        val result = latestFrameResult
        val estimate = if (result != null) {
            estimateDistance(result, detection)
        } else {
            latestDistanceEstimate
        }
        latestDistanceEstimate = estimate
        distanceText.text = "Distance calibrated at 40cm. Eye distance=${eyeDistancePx.roundToInt()}px."
    }

    private fun buildEyeCoordinateText(
        result: EyeFrameResult,
        detection: FaceEyeDetection,
        distanceEstimate: DistanceEstimate
    ): String {
        val leftEye = detection.leftEye
        val rightEye = detection.rightEye
        val left = leftEye?.let {
            val center = it.center
            "L center=(${center.x.roundToInt()},${center.y.roundToInt()})"
        } ?: "L unavailable"
        val right = rightEye?.let {
            val center = it.center
            "R center=(${center.x.roundToInt()},${center.y.roundToInt()})"
        } ?: "R unavailable"
        val face = detection.faceBox
        val multiFace = if (result.faceCount > 1) "  MULTI_FACE=${result.faceCount}" else ""
        return String.format(
            Locale.US,
            "Eye detection: face=%d%s  fps=%.1f  latency=%dms  source=%dx%d  distance=%s  faceBox=(%d,%d)-(%d,%d)\n%s\n%s",
            detection.faceId,
            multiFace,
            result.fps,
            result.detectionLatencyMillis,
            result.sourceWidth,
            result.sourceHeight,
            formatDistanceForInline(distanceEstimate),
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
        detection: FaceEyeDetection,
        distanceEstimate: DistanceEstimate
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
            "\"left_eye_center\":${left?.center?.let { pointToJson(it.x, it.y) } ?: "null"}," +
            "\"right_eye_center\":${right?.center?.let { pointToJson(it.x, it.y) } ?: "null"}," +
            "\"eye_distance_px\":${distanceEstimate.eyeDistancePx?.roundToInt() ?: "null"}," +
            "\"estimated_distance_cm\":${distanceEstimate.estimatedDistanceCm?.roundToInt() ?: "null"}," +
            "\"distance_state\":\"${distanceEstimate.state}\"," +
            "\"distance_confidence\":${String.format(Locale.US, "%.2f", distanceEstimate.confidence)}," +
            "\"distance_method\":\"${distanceEstimate.method}\"" +
            "}"
    }

    private fun buildInitialDistanceText(): String {
        val calibration = if (::distanceEstimator.isInitialized) {
            distanceEstimator.getCalibrationEyeDistancePx()
        } else {
            null
        }
        return if (calibration == null) {
            "Distance: not calibrated. Hold the tablet about 40cm away and tap Calibrate 40cm."
        } else {
            "Distance: calibrated. Start camera to estimate eye-to-screen distance."
        }
    }

    private fun buildDistanceText(estimate: DistanceEstimate): String {
        val distance = estimate.estimatedDistanceCm?.let {
            "${it.roundToInt()}cm"
        } ?: "--"
        val eyeDistance = estimate.eyeDistancePx?.let {
            "${it.roundToInt()}px"
        } ?: "--"
        val stateText = when (estimate.state) {
            DistanceState.NEEDS_CALIBRATION -> "NEEDS_CALIBRATION"
            DistanceState.TOO_CLOSE -> "TOO_CLOSE"
            DistanceState.NORMAL -> "NORMAL"
            DistanceState.FAR -> "FAR"
            DistanceState.UNKNOWN -> "UNKNOWN"
        }
        return String.format(
            Locale.US,
            "Distance: %s  state=%s  eyeDistance=%s  confidence=%.2f",
            distance,
            stateText,
            eyeDistance,
            estimate.confidence
        )
    }

    private fun formatDistanceForInline(estimate: DistanceEstimate): String {
        return estimate.estimatedDistanceCm?.let {
            "${it.roundToInt()}cm/${estimate.state}"
        } ?: estimate.state.toString()
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
