package com.example.eyeprotect.usage

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeInForegroundMillis: Long,
    val lastTimeUsedMillis: Long
)
