from __future__ import annotations

from typing import Any


class Camera:
    def __init__(
        self,
        camera_id: int = 0,
        width: int = 1280,
        height: int = 720,
    ) -> None:
        self.camera_id = camera_id
        self.width = width
        self.height = height
        self._capture: Any | None = None

    def open(self) -> None:
        try:
            import cv2
        except ImportError as exc:
            raise RuntimeError(
                "OpenCV is not installed. Run: pip install opencv-python"
            ) from exc

        capture = cv2.VideoCapture(self.camera_id)
        if not capture.isOpened():
            capture.release()
            raise RuntimeError(
                f"Camera {self.camera_id} cannot be opened. "
                "Check camera permission, close other camera apps, or try camera_id=1."
            )

        capture.set(cv2.CAP_PROP_FRAME_WIDTH, self.width)
        capture.set(cv2.CAP_PROP_FRAME_HEIGHT, self.height)
        self._capture = capture

    def read(self) -> tuple[bool, Any]:
        if self._capture is None:
            raise RuntimeError("Camera is not open. Call Camera.open() first.")
        return self._capture.read()

    def release(self) -> None:
        if self._capture is not None:
            self._capture.release()
            self._capture = None
