package com.example.eyeprotect.vision

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

class EyePositionAnalyzer(
    private val onResult: (EyeFrameResult) -> Unit,
    private val onError: (Throwable) -> Unit
) : ImageAnalysis.Analyzer {
    private val detector: FaceDetector
    @Volatile
    private var isProcessing = false
    private var previousFrameNanos = 0L
    private var smoothedFps = 0f
    private var frameIndex = 0

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
        frameIndex += 1
        if (frameIndex % DETECTION_FRAME_INTERVAL != 0) {
            imageProxy.close()
            return
        }

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
                                fps = fps
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
        fps: Float
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
                sourceHeight = sourceHeight
            ),
            rightEye = extractEye(
                faceId = faceId,
                label = "RIGHT EYE",
                contourType = FaceContour.RIGHT_EYE,
                landmarkType = FaceLandmark.RIGHT_EYE,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight
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
        sourceHeight: Int
    ): EyeDetection? {
        val contourPoints = getContour(contourType)?.points.orEmpty()
        val points = if (contourPoints.isNotEmpty()) {
            contourPoints
        } else {
            val center = getLandmark(landmarkType)?.position ?: return null
            fallbackEyePoints(center)
        }

        val safePoints = points.map {
            PointF(
                it.x.coerceIn(0f, sourceWidth - 1f),
                it.y.coerceIn(0f, sourceHeight - 1f)
            )
        }
        return EyeDetection(
            label = label,
            points = safePoints,
            center = calculateCenter(safePoints)
        )
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

    private fun calculateCenter(points: List<PointF>): PointF {
        if (points.isEmpty()) {
            return PointF(0f, 0f)
        }
        return PointF(
            points.sumOf { it.x.toDouble() }.toFloat() / points.size,
            points.sumOf { it.y.toDouble() }.toFloat() / points.size
        )
    }

    private fun Rect.toBoundingBox(sourceWidth: Int, sourceHeight: Int): BoundingBox {
        return BoundingBox(
            x1 = left.toFloat().coerceIn(0f, sourceWidth - 1f),
            y1 = top.toFloat().coerceIn(0f, sourceHeight - 1f),
            x2 = right.toFloat().coerceIn(0f, sourceWidth - 1f),
            y2 = bottom.toFloat().coerceIn(0f, sourceHeight - 1f)
        )
    }

    companion object {
        private const val DETECTION_FRAME_INTERVAL = 4
    }
}
