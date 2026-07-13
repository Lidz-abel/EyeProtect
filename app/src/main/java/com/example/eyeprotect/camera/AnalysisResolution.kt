package com.example.eyeprotect.camera

import android.util.Size

enum class AnalysisResolution(
    val key: String,
    val label: String,
    val size: Size
) {
    LOW_POWER(
        key = "low_power",
        label = "Low 320x240",
        size = Size(320, 240)
    ),
    BALANCED(
        key = "balanced",
        label = "Balanced 640x480",
        size = Size(640, 480)
    ),
    HIGH_QUALITY(
        key = "high_quality",
        label = "HD 1280x720",
        size = Size(1280, 720)
    );

    companion object {
        fun fromKey(key: String?): AnalysisResolution {
            return entries.firstOrNull { it.key == key } ?: BALANCED
        }
    }
}
