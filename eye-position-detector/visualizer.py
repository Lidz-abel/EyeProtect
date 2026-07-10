from __future__ import annotations

from typing import Any

from config import DRAW_BOUNDING_BOX, DRAW_LANDMARKS
from models import EyeDetection, FaceEyeDetection


LEFT_COLOR = (0, 255, 0)
RIGHT_COLOR = (255, 200, 0)
POINT_COLOR = (0, 0, 255)
TEXT_COLOR = (255, 255, 255)
WARNING_COLOR = (0, 180, 255)


def draw_eye_detection(frame: Any, detection: FaceEyeDetection) -> None:
    _draw_eye(frame, detection.left_eye, LEFT_COLOR)
    _draw_eye(frame, detection.right_eye, RIGHT_COLOR)


def draw_status(frame: Any, fps: float, face_detected: bool) -> None:
    import cv2

    status = "FACE DETECTED" if face_detected else "NO FACE"
    color = LEFT_COLOR if face_detected else WARNING_COLOR

    cv2.putText(
        frame,
        f"FPS: {fps:.1f}",
        (16, 28),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.75,
        TEXT_COLOR,
        2,
    )
    cv2.putText(
        frame,
        status,
        (16, 60),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.75,
        color,
        2,
    )


def _draw_eye(frame: Any, eye: EyeDetection, color: tuple[int, int, int]) -> None:
    import cv2

    x1, y1, x2, y2 = eye.box.as_tuple()

    if DRAW_BOUNDING_BOX:
        cv2.rectangle(frame, (x1, y1), (x2, y2), color, 2)

    if DRAW_LANDMARKS:
        for x, y in eye.points:
            cv2.circle(frame, (x, y), 1, POINT_COLOR, -1)

    label_y = max(18, y1 - 8)
    cv2.putText(
        frame,
        eye.label,
        (x1, label_y),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.5,
        color,
        2,
    )
