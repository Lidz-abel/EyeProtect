# EyeProtect

Android tablet app prototype for:

- Reading app/screen usage time through `UsageStatsManager`.
- Opening the front camera through CameraX.
- Detecting left/right eye positions with ML Kit Face Detection.
- Stabilizing eye boxes with smoothing, FPS, latency, face box, and center-point output.
- Cropping and displaying left/right eye ROI images.

## Run

Open this directory in Android Studio, sync Gradle, and run the `app` module on the Huawei tablet.

Before usage data can be shown, open `Usage Access` in the app and allow access for EyeProtect in Android settings.

For camera preview, tap `Start Front Camera` and allow camera permission.
The preview will draw left/right eye bounding boxes when a face is detected.
The app also shows left/right eye ROI previews below the camera preview.

## Code Structure

- `app/src/main/java/com/example/eyeprotect/usage/`
  - `UsageAccessHelper.kt`: checks usage-access permission and creates the settings intent.
  - `ScreenUsageRepository.kt`: reads today's foreground usage by app.
  - `AppUsageInfo.kt`: usage data model.
- `app/src/main/java/com/example/eyeprotect/camera/`
  - `FrontCameraController.kt`: binds CameraX preview and image analysis to the front camera.
- `app/src/main/java/com/example/eyeprotect/vision/`
  - `EyePositionAnalyzer.kt`: runs ML Kit face detection on CameraX frames.
  - `ImageProxyBitmapConverter.kt`: converts CameraX frames to rotated bitmaps for ROI cropping.
  - `FaceEyeDetection.kt`, `EyeDetection.kt`, `BoundingBox.kt`: detection result models.
- `app/src/main/java/com/example/eyeprotect/ui/`
  - `EyeDetectionOverlay.kt`: draws eye landmarks and bounding boxes over the camera preview.
- `app/src/main/java/com/example/eyeprotect/MainActivity.kt`
  - Simple validation UI for screen usage, front camera, and eye-position detection.
