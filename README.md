# EyeProtect

Android tablet app prototype for:

- Reading app/screen usage time through `UsageStatsManager`.
- Opening the front camera through CameraX.
- Detecting left/right eye positions with ML Kit Face Detection.
- Drawing lightweight red eye landmark points with FPS, latency, face box, and center-point output.
- Estimating eye-to-screen distance from the left/right eye center distance after a 40cm calibration.
- Releasing the camera when the app goes to background or the screen turns off.
- Restoring the camera on foreground return when it was running before pause.

## Run

Open this directory in Android Studio, sync Gradle, and run the `app` module on the Huawei tablet.

Before usage data can be shown, open `Usage Access` in the app and allow access for EyeProtect in Android settings.

For camera preview, tap `Start Front Camera` and allow camera permission.
The preview will draw red left/right eye landmark points when a face is detected.
For distance estimation, hold the tablet about 40cm from the eyes and tap `Calibrate 40cm`; subsequent frames will show estimated distance and `TOO_CLOSE` / `NORMAL` / `FAR` state.
Use the `Low`, `Balanced`, and `HD` controls to switch CameraX image-analysis resolution between low-power, balanced, and high-quality modes.
Tap `Stop Camera` to release the camera without leaving the app.

## Code Structure

- `app/src/main/java/com/example/eyeprotect/usage/`
  - `UsageAccessHelper.kt`: checks usage-access permission and creates the settings intent.
  - `ScreenUsageRepository.kt`: reads today's foreground usage by app.
  - `AppUsageInfo.kt`: usage data model.
- `app/src/main/java/com/example/eyeprotect/camera/`
  - `AnalysisResolution.kt`: defines low-power, balanced, and high-quality analysis sizes.
  - `FrontCameraController.kt`: binds CameraX preview and image analysis to the front camera.
- `app/src/main/java/com/example/eyeprotect/vision/`
  - `EyePositionAnalyzer.kt`: runs ML Kit face detection on CameraX frames.
  - `EyeDistanceEstimator.kt`: estimates eye-to-screen distance from calibrated eye-center pixel distance.
  - `FaceEyeDetection.kt`, `EyeDetection.kt`, `BoundingBox.kt`: detection result models.
- `app/src/main/java/com/example/eyeprotect/ui/`
  - `EyeDetectionOverlay.kt`: draws red eye landmark points over the camera preview.
- `app/src/main/java/com/example/eyeprotect/MainActivity.kt`
  - Simple validation UI for screen usage, front camera, and eye-position detection.
