package com.example.eyeprotect.vision

data class DistanceEstimate(
    val eyeDistancePx: Float?,
    val estimatedDistanceCm: Float?,
    val state: DistanceState,
    val confidence: Float,
    val method: String,
    val calibrated: Boolean
)
