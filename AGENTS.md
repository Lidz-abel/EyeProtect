# Repository Guidelines

## Project Structure & Module Organization

This repository contains an Android tablet app plus a Python prototype.

- `app/`: Android application module.
- `app/src/main/java/com/example/eyeprotect/`: Kotlin source code.
  - `camera/`: CameraX preview and frame analysis wiring.
  - `vision/`: ML Kit face/eye detection, frame conversion, result models.
  - `ui/`: overlay drawing for eye boxes and landmarks.
  - `usage/`: Android screen/app usage-time access.
- `app/src/main/res/`: Android resources, styles, icons, and colors.
- `eye-position-detector/`: Python OpenCV/MediaPipe prototype and tests.
- `docs/`: generated technical overview PDF and LaTeX source.

## Build, Test, and Development Commands

Run commands from the repository root unless noted.

```powershell
.\gradlew.bat assembleDebug
```

Builds the Android debug APK at `app/build/outputs/apk/debug/app-debug.apk`.

```powershell
cd eye-position-detector
python -m unittest discover -s tests
```

Runs the Python prototype unit tests.

```powershell
cd docs
xelatex -interaction=nonstopmode -halt-on-error eyeprotect_technical_overview.tex
```

Regenerates the technical overview PDF.

## Coding Style & Naming Conventions

Use Kotlin for Android code and Python for the prototype. Kotlin files use 4-space indentation, clear package grouping, and descriptive class names such as `EyePositionAnalyzer` or `FrontCameraController`. Keep Android responsibilities separated by package: camera capture, vision processing, UI drawing, and usage statistics should not be mixed.

Python code follows standard `snake_case` naming for functions and files, with dataclasses in `models.py`.

## Testing Guidelines

Android changes should at minimum build with `.\gradlew.bat assembleDebug`. For camera or ML Kit changes, manually verify on the Huawei tablet: camera start/stop, eye boxes, ROI previews, background release, and lock-screen behavior.

Python tests live in `eye-position-detector/tests/` and use `unittest`. Name tests `test_*.py`.

## Commit & Pull Request Guidelines

Git history currently contains only `initial commit`, so no strict commit convention is established. Use short imperative messages, for example `Add eye ROI preview` or `Fix camera lifecycle handling`.

Pull requests should include a concise summary, affected modules, test/build results, and screenshots or short screen recordings for UI or camera behavior changes.

## Security & Configuration Tips

Do not commit `local.properties`, build outputs, Gradle caches, APKs, or device-specific credentials. ML Kit detection runs on-device; Gradle dependency downloads may require network access during development.
