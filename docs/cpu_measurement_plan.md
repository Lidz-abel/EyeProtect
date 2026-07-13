# CPU Measurement Plan

## Goal

Measure whether EyeProtect is lightweight enough for sustained use on the Huawei tablet.

## Metrics

- App CPU usage for `com.example.eyeprotect`
- In-app `fps`
- In-app `latency`
- CPU behavior after backgrounding or locking the screen
- Optional: memory and energy trend in Android Studio Profiler

## ADB Commands

Check device connection:

```powershell
adb devices
```

Single CPU snapshot:

```powershell
adb shell dumpsys cpuinfo | findstr eyeprotect
```

Continuous 60-second sampling:

```powershell
1..60 | ForEach-Object {
    adb shell dumpsys cpuinfo | findstr eyeprotect
    Start-Sleep -Seconds 1
}
```

PID-based live view:

```powershell
adb shell pidof com.example.eyeprotect
adb shell top -p <PID>
```

## Test Scenarios

1. App open, camera not started.
2. Camera started, no face in frame.
3. Camera started, one face centered.
4. Head movement while tracking.
5. Press Home and keep app in background.
6. Lock screen while camera is running.
7. Return to foreground.

## Evaluation Criteria

- `fps >= 20`: acceptable real-time performance.
- `latency < 80ms`: acceptable detection delay.
- CPU `< 10%`: light.
- CPU `10%~25%`: acceptable.
- CPU `25%~40%`: high, consider optimization.
- CPU `> 40%`: needs optimization.
- Background or locked-screen CPU should drop close to idle because camera is released.

## Possible Optimizations If Needed

- Lower `ImageAnalysis` resolution below `640x480`.
- Analyze every 2nd or 3rd frame.
- Limit detection rate to `10~15 FPS`.
- Avoid ROI bitmap cropping every frame.
- Disable ROI preview unless debugging.
