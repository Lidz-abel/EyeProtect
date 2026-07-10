import sys
import unittest
from dataclasses import dataclass
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))

from eye_detector import landmarks_to_pixel_points


@dataclass
class FakeLandmark:
    x: float
    y: float


class LandmarkConversionTest(unittest.TestCase):
    def test_converts_normalized_landmarks_to_pixel_points(self):
        landmarks = [
            FakeLandmark(0.0, 0.0),
            FakeLandmark(0.5, 0.25),
            FakeLandmark(1.0, 0.75),
        ]

        points = landmarks_to_pixel_points(
            landmarks=landmarks,
            indices=[0, 1, 2],
            frame_width=640,
            frame_height=480,
        )

        self.assertEqual(points, [(0, 0), (320, 120), (640, 360)])

    def test_respects_requested_indices_order(self):
        landmarks = [
            FakeLandmark(0.1, 0.2),
            FakeLandmark(0.3, 0.4),
            FakeLandmark(0.5, 0.6),
        ]

        points = landmarks_to_pixel_points(
            landmarks=landmarks,
            indices=[2, 0],
            frame_width=100,
            frame_height=100,
        )

        self.assertEqual(points, [(50, 60), (10, 20)])


if __name__ == "__main__":
    unittest.main()
