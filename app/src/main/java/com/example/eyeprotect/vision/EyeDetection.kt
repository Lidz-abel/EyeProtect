package com.example.eyeprotect.vision

import android.graphics.PointF

data class EyeDetection(
    val label: String,
    val points: List<PointF>,
    val center: PointF
)
