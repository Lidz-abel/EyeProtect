from __future__ import annotations

from typing import Any, Sequence

from config import (
    EYE_BOX_PADDING,
    LEFT_EYE_INDICES,
    MAX_NUM_FACES,
    MIN_DETECTION_CONFIDENCE,
    MIN_TRACKING_CONFIDENCE,
    RIGHT_EYE_INDICES,
)
from models import BoundingBox, EyeDetection, FaceEyeDetection, Point


def landmarks_to_pixel_points(
    landmarks: Sequence[Any],
    indices: Sequence[int],
    frame_width: int,
    frame_height: int,
) -> list[Point]:
    points: list[Point] = []

    for index in indices:
        landmark = landmarks[index]
        x = int(landmark.x * frame_width)
        y = int(landmark.y * frame_height)
        points.append((x, y))

    return points


def calculate_bounding_box(
    points: Sequence[Point],
    padding: int,
    frame_width: int,
    frame_height: int,
) -> BoundingBox:
    if not points:
        raise ValueError("Cannot calculate a bounding box from zero points.")

    x_values = [point[0] for point in points]
    y_values = [point[1] for point in points]

    x1 = min(x_values) - padding
    y1 = min(y_values) - padding
    x2 = max(x_values) + padding
    y2 = max(y_values) + padding

    x1 = max(0, x1)
    y1 = max(0, y1)
    x2 = min(frame_width - 1, x2)
    y2 = min(frame_height - 1, y2)

    return BoundingBox(x1=x1, y1=y1, x2=x2, y2=y2)


class EyeDetector:
    def __init__(
        self,
        max_num_faces: int = MAX_NUM_FACES,
        min_detection_confidence: float = MIN_DETECTION_CONFIDENCE,
        min_tracking_confidence: float = MIN_TRACKING_CONFIDENCE,
        eye_box_padding: int = EYE_BOX_PADDING,
    ) -> None:
        try:
            import mediapipe as mp
        except ImportError as exc:
            raise RuntimeError("MediaPipe is not installed. Run: pip install mediapipe") from exc

        self.eye_box_padding = eye_box_padding
        self._face_mesh = mp.solutions.face_mesh.FaceMesh(
            static_image_mode=False,
            max_num_faces=max_num_faces,
            refine_landmarks=True,
            min_detection_confidence=min_detection_confidence,
            min_tracking_confidence=min_tracking_confidence,
        )

    def detect(self, rgb_frame: Any) -> list[FaceEyeDetection]:
        frame_height, frame_width = rgb_frame.shape[:2]
        result = self._face_mesh.process(rgb_frame)

        if not result.multi_face_landmarks:
            return []

        detections: list[FaceEyeDetection] = []
        for face_id, face_landmarks in enumerate(result.multi_face_landmarks):
            landmarks = face_landmarks.landmark

            left_points = landmarks_to_pixel_points(
                landmarks=landmarks,
                indices=LEFT_EYE_INDICES,
                frame_width=frame_width,
                frame_height=frame_height,
            )
            right_points = landmarks_to_pixel_points(
                landmarks=landmarks,
                indices=RIGHT_EYE_INDICES,
                frame_width=frame_width,
                frame_height=frame_height,
            )

            left_eye = EyeDetection(
                label="LEFT EYE",
                points=left_points,
                box=calculate_bounding_box(
                    left_points,
                    padding=self.eye_box_padding,
                    frame_width=frame_width,
                    frame_height=frame_height,
                ),
            )
            right_eye = EyeDetection(
                label="RIGHT EYE",
                points=right_points,
                box=calculate_bounding_box(
                    right_points,
                    padding=self.eye_box_padding,
                    frame_width=frame_width,
                    frame_height=frame_height,
                ),
            )

            detections.append(
                FaceEyeDetection(
                    face_id=face_id,
                    left_eye=left_eye,
                    right_eye=right_eye,
                )
            )

        return detections

    def close(self) -> None:
        self._face_mesh.close()
