# Eye Position Detector

Minimal real-time eye-position detector:

```text
Front camera
-> BGR video frame
-> optional horizontal mirror
-> RGB conversion
-> MediaPipe Face Mesh
-> left/right eye landmark extraction
-> pixel coordinate conversion
-> eye bounding boxes
-> OpenCV visualization and console JSON output
```

This stage only detects eye positions. It does not implement iris tracking,
gaze direction, blink detection, fatigue detection, or screen gaze prediction.

## Environment

Recommended Python version: 3.10 or 3.11.

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

If the default camera cannot be opened, edit `config.py` and try:

```python
CAMERA_ID = 1
```

## Run

```powershell
python main.py
```

Controls:

- Press `q` to exit.
- Press `Esc` to exit.

The program prints JSON every `OUTPUT_EVERY_N_FRAMES` frames:

```json
{
  "timestamp": 1783651200.125,
  "frame_id": 230,
  "face_detected": true,
  "faces": [
    {
      "face_id": 0,
      "left_eye": {
        "label": "LEFT EYE",
        "points": [[212, 164]],
        "box": {"x1": 212, "y1": 164, "x2": 279, "y2": 198}
      },
      "right_eye": {
        "label": "RIGHT EYE",
        "points": [[334, 163]],
        "box": {"x1": 334, "y1": 163, "x2": 401, "y2": 197}
      }
    }
  ]
}
```

## Files

- `main.py`: runtime loop and resource cleanup.
- `camera.py`: OpenCV camera wrapper.
- `eye_detector.py`: MediaPipe Face Mesh integration, eye landmarks, bounding boxes.
- `visualizer.py`: OpenCV drawing functions.
- `models.py`: dataclasses for detection output.
- `config.py`: camera, detection, drawing, and landmark-index configuration.
- `tests/`: pure unit tests for coordinate conversion and bounding boxes.

## Tests

The unit tests do not require a camera.

```powershell
python -m unittest discover -s tests
```
