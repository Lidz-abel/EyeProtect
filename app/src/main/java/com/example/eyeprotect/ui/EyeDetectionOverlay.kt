package com.example.eyeprotect.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.example.eyeprotect.vision.EyeDetection
import com.example.eyeprotect.vision.FaceEyeDetection

class EyeDetectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
    }
    private val noFacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 210, 80)
        textSize = 32f
        strokeWidth = 2f
    }

    private var detections: List<FaceEyeDetection> = emptyList()
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var mirror = true

    fun setDetections(
        detections: List<FaceEyeDetection>,
        sourceWidth: Int,
        sourceHeight: Int,
        mirror: Boolean
    ) {
        this.detections = detections
        this.sourceWidth = sourceWidth
        this.sourceHeight = sourceHeight
        this.mirror = mirror
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return
        }

        if (detections.isEmpty()) {
            canvas.drawText("NO FACE", 24f, 42f, noFacePaint)
            return
        }

        detections.forEach { detection ->
            detection.leftEye?.let { drawEye(canvas, it) }
            detection.rightEye?.let { drawEye(canvas, it) }
        }
    }

    private fun drawEye(canvas: Canvas, eye: EyeDetection) {
        eye.points.forEach { point ->
            val mapped = mapPoint(point)
            canvas.drawCircle(mapped.x, mapped.y, 4f, pointPaint)
        }
    }

    private fun mapPoint(point: PointF): PointF {
        val scale = maxOf(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
        val scaledWidth = sourceWidth * scale
        val scaledHeight = sourceHeight * scale
        val dx = (width - scaledWidth) / 2f
        val dy = (height - scaledHeight) / 2f

        val mappedX = point.x * scale + dx
        val mappedY = point.y * scale + dy

        return if (mirror) {
            PointF(width - mappedX, mappedY)
        } else {
            PointF(mappedX, mappedY)
        }
    }
}
