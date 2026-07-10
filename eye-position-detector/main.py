from __future__ import annotations

import json
import time

import config
from camera import Camera
from eye_detector import EyeDetector
from visualizer import draw_eye_detection, draw_status


def main() -> None:
    try:
        import cv2
    except ImportError as exc:
        raise RuntimeError("OpenCV is not installed. Run: pip install opencv-python") from exc

    camera = Camera(
        camera_id=config.CAMERA_ID,
        width=config.CAMERA_WIDTH,
        height=config.CAMERA_HEIGHT,
    )
    detector = EyeDetector(
        max_num_faces=config.MAX_NUM_FACES,
        min_detection_confidence=config.MIN_DETECTION_CONFIDENCE,
        min_tracking_confidence=config.MIN_TRACKING_CONFIDENCE,
        eye_box_padding=config.EYE_BOX_PADDING,
    )

    frame_index = 0
    previous_time = time.perf_counter()
    smoothed_fps = 0.0

    camera.open()
    try:
        while True:
            success, frame = camera.read()
            if not success:
                print("Failed to read frame from camera.")
                break

            if config.MIRROR_FRAME:
                frame = cv2.flip(frame, 1)

            rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            detections = detector.detect(rgb_frame)

            for detection in detections:
                draw_eye_detection(frame, detection)

            current_time = time.perf_counter()
            fps = 1.0 / max(current_time - previous_time, 1e-6)
            previous_time = current_time
            smoothed_fps = fps if smoothed_fps == 0.0 else 0.9 * smoothed_fps + 0.1 * fps

            draw_status(frame, fps=smoothed_fps, face_detected=bool(detections))

            if frame_index % config.OUTPUT_EVERY_N_FRAMES == 0:
                print_detection(frame_index=frame_index, detections=detections)

            cv2.imshow(config.WINDOW_NAME, frame)
            key = cv2.waitKey(1) & 0xFF
            if key in (ord("q"), 27):
                break

            frame_index += 1
    finally:
        camera.release()
        detector.close()
        cv2.destroyAllWindows()


def print_detection(frame_index: int, detections: list) -> None:
    payload = {
        "timestamp": time.time(),
        "frame_id": frame_index,
        "face_detected": bool(detections),
        "faces": [detection.to_dict() for detection in detections],
    }
    print(json.dumps(payload, ensure_ascii=False))


if __name__ == "__main__":
    main()
