package com.example.eyeprotect.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.eyeprotect.vision.BoundingBox
import com.example.eyeprotect.vision.EyeDetection
import com.example.eyeprotect.vision.FaceEyeDetection

class EyeDetectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.rgb(20, 220, 120)
    }
    private val rightBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.rgb(80, 180, 255)
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(255, 70, 70)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        strokeWidth = 2f
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
            detection.leftEye?.let { drawEye(canvas, it, boxPaint) }
            detection.rightEye?.let { drawEye(canvas, it, rightBoxPaint) }
        }
    }

    private fun drawEye(canvas: Canvas, eye: EyeDetection, paint: Paint) {
        val rect = mapBox(eye.box)
        canvas.drawRect(rect, paint)

        eye.points.forEach { point ->
            val mapped = mapPoint(point)
            canvas.drawCircle(mapped.x, mapped.y, 3f, pointPaint)
        }

        canvas.drawText(
            eye.label,
            rect.left,
            (rect.top - 10f).coerceAtLeast(28f),
            textPaint
        )
    }

    private fun mapBox(box: BoundingBox): RectF {
        val topLeft = mapPoint(PointF(box.x1, box.y1))
        val bottomRight = mapPoint(PointF(box.x2, box.y2))
        return RectF(
            minOf(topLeft.x, bottomRight.x),
            minOf(topLeft.y, bottomRight.y),
            maxOf(topLeft.x, bottomRight.x),
            maxOf(topLeft.y, bottomRight.y)
        )
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
