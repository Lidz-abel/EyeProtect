package com.example.eyeprotect.vision

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.roundToInt

class EyePositionAnalyzer(
    private val onResult: (EyeFrameResult) -> Unit,
    private val onError: (Throwable) -> Unit
) : ImageAnalysis.Analyzer {
    private val detector: FaceDetector
    private val previousEyes = mutableMapOf<String, PreviousEye>()
    @Volatile
    private var isProcessing = false
    private var previousFrameNanos = 0L
    private var smoothedFps = 0f

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()

        detector = FaceDetection.getClient(options)
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isProcessing = true
        val analyzeStartNanos = System.nanoTime()
        val timestampMillis = System.currentTimeMillis()
        val fps = updateFps(analyzeStartNanos)
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        val frameBitmap = runCatching {
            imageProxy.toRotatedBitmap(rotationDegrees)
        }.getOrNull()
        val sourceWidth = if (rotationDegrees == 90 || rotationDegrees == 270) {
            imageProxy.height
        } else {
            imageProxy.width
        }
        val sourceHeight = if (rotationDegrees == 90 || rotationDegrees == 270) {
            imageProxy.width
        } else {
            imageProxy.height
        }

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                val latencyMillis = ((System.nanoTime() - analyzeStartNanos) / 1_000_000L)
                if (faces.isEmpty()) {
                    previousEyes.clear()
                }

                onResult(
                    EyeFrameResult(
                        detections = faces.mapIndexed { index, face ->
                        face.toEyeDetection(
                            faceId = face.trackingId ?: index,
                            sourceWidth = sourceWidth,
                            sourceHeight = sourceHeight,
                                rotationDegrees = rotationDegrees,
                                timestampMillis = timestampMillis,
                                detectionLatencyMillis = latencyMillis,
                                fps = fps,
                                frameBitmap = frameBitmap
                        )
                        },
                        sourceWidth = sourceWidth,
                        sourceHeight = sourceHeight,
                        timestampMillis = timestampMillis,
                        detectionLatencyMillis = latencyMillis,
                        fps = fps
                    )
                )
            }
            .addOnFailureListener { throwable ->
                onError(throwable)
            }
            .addOnCompleteListener {
                isProcessing = false
                imageProxy.close()
            }
    }

    fun close() {
        detector.close()
    }

    private fun updateFps(nowNanos: Long): Float {
        if (previousFrameNanos == 0L) {
            previousFrameNanos = nowNanos
            return smoothedFps
        }

        val frameFps = 1_000_000_000f / (nowNanos - previousFrameNanos).coerceAtLeast(1L)
        previousFrameNanos = nowNanos
        smoothedFps = if (smoothedFps == 0f) {
            frameFps
        } else {
            0.85f * smoothedFps + 0.15f * frameFps
        }
        return smoothedFps
    }

    private fun Face.toEyeDetection(
        faceId: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        rotationDegrees: Int,
        timestampMillis: Long,
        detectionLatencyMillis: Long,
        fps: Float,
        frameBitmap: Bitmap?
    ): FaceEyeDetection {
        return FaceEyeDetection(
            faceId = faceId,
            faceBox = boundingBox.toBoundingBox(sourceWidth, sourceHeight),
            leftEye = extractEye(
                faceId = faceId,
                label = "LEFT EYE",
                contourType = FaceContour.LEFT_EYE,
                landmarkType = FaceLandmark.LEFT_EYE,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                frameBitmap = frameBitmap
            ),
            rightEye = extractEye(
                faceId = faceId,
                label = "RIGHT EYE",
                contourType = FaceContour.RIGHT_EYE,
                landmarkType = FaceLandmark.RIGHT_EYE,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                frameBitmap = frameBitmap
            ),
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            rotationDegrees = rotationDegrees,
            timestampMillis = timestampMillis,
            detectionLatencyMillis = detectionLatencyMillis,
            fps = fps
        )
    }

    private fun Face.extractEye(
        faceId: Int,
        label: String,
        contourType: Int,
        landmarkType: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        frameBitmap: Bitmap?
    ): EyeDetection? {
        val contourPoints = getContour(contourType)?.points.orEmpty()
        val points = if (contourPoints.isNotEmpty()) {
            contourPoints
        } else {
            val center = getLandmark(landmarkType)?.position ?: return null
            fallbackEyePoints(center)
        }

        val rawBox = calculateBoundingBox(
            points = points,
            padding = 8f,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight
        )
        val smoothed = smoothEye(
            key = "$faceId:$label",
            points = points,
            box = rawBox
        )

        return EyeDetection(
            label = label,
            points = smoothed.points,
            box = smoothed.box,
            center = PointF(smoothed.box.centerX, smoothed.box.centerY),
            roiBitmap = cropEyeRoi(frameBitmap, smoothed.box)
        )
    }

    private fun smoothEye(
        key: String,
        points: List<PointF>,
        box: BoundingBox
    ): PreviousEye {
        val previous = previousEyes[key]
        val alpha = 0.35f
        val smoothedPoints = if (previous != null && previous.points.size == points.size) {
            points.mapIndexed { index, point ->
                val previousPoint = previous.points[index]
                PointF(
                    previousPoint.x + alpha * (point.x - previousPoint.x),
                    previousPoint.y + alpha * (point.y - previousPoint.y)
                )
            }
        } else {
            points
        }
        val smoothedBox = if (previous != null) {
            BoundingBox(
                x1 = previous.box.x1 + alpha * (box.x1 - previous.box.x1),
                y1 = previous.box.y1 + alpha * (box.y1 - previous.box.y1),
                x2 = previous.box.x2 + alpha * (box.x2 - previous.box.x2),
                y2 = previous.box.y2 + alpha * (box.y2 - previous.box.y2)
            )
        } else {
            box
        }
        val smoothed = PreviousEye(smoothedPoints, smoothedBox)
        previousEyes[key] = smoothed
        return smoothed
    }

    private fun fallbackEyePoints(center: PointF): List<PointF> {
        val halfWidth = 28f
        val halfHeight = 14f
        return listOf(
            PointF(center.x - halfWidth, center.y - halfHeight),
            PointF(center.x + halfWidth, center.y - halfHeight),
            PointF(center.x + halfWidth, center.y + halfHeight),
            PointF(center.x - halfWidth, center.y + halfHeight)
        )
    }

    private fun calculateBoundingBox(
        points: List<PointF>,
        padding: Float,
        sourceWidth: Int,
        sourceHeight: Int
    ): BoundingBox {
        val x1 = (points.minOf { it.x } - padding).coerceAtLeast(0f)
        val y1 = (points.minOf { it.y } - padding).coerceAtLeast(0f)
        val x2 = (points.maxOf { it.x } + padding).coerceAtMost(sourceWidth - 1f)
        val y2 = (points.maxOf { it.y } + padding).coerceAtMost(sourceHeight - 1f)
        return BoundingBox(x1 = x1, y1 = y1, x2 = x2, y2 = y2)
    }

    private fun Rect.toBoundingBox(sourceWidth: Int, sourceHeight: Int): BoundingBox {
        return BoundingBox(
            x1 = left.toFloat().coerceIn(0f, sourceWidth - 1f),
            y1 = top.toFloat().coerceIn(0f, sourceHeight - 1f),
            x2 = right.toFloat().coerceIn(0f, sourceWidth - 1f),
            y2 = bottom.toFloat().coerceIn(0f, sourceHeight - 1f)
        )
    }

    private fun cropEyeRoi(frameBitmap: Bitmap?, box: BoundingBox): Bitmap? {
        if (frameBitmap == null || box.width < 2f || box.height < 2f) {
            return null
        }

        val scale = 1.8f
        val cropWidth = box.width * scale
        val cropHeight = box.height * scale
        val left = (box.centerX - cropWidth / 2f).roundToInt().coerceAtLeast(0)
        val top = (box.centerY - cropHeight / 2f).roundToInt().coerceAtLeast(0)
        val right = (box.centerX + cropWidth / 2f).roundToInt().coerceAtMost(frameBitmap.width)
        val bottom = (box.centerY + cropHeight / 2f).roundToInt().coerceAtMost(frameBitmap.height)

        val width = right - left
        val height = bottom - top
        if (width < 2 || height < 2) {
            return null
        }

        return Bitmap.createBitmap(frameBitmap, left, top, width, height)
    }

    private data class PreviousEye(
        val points: List<PointF>,
        val box: BoundingBox
    )
}
