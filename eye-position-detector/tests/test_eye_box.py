import sys
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))

from eye_detector import calculate_bounding_box
from models import BoundingBox


class EyeBoxTest(unittest.TestCase):
    def test_calculates_box_without_padding(self):
        points = [
            (100, 100),
            (120, 90),
            (140, 105),
            (115, 110),
        ]

        box = calculate_bounding_box(
            points=points,
            padding=0,
            frame_width=640,
            frame_height=480,
        )

        self.assertEqual(box, BoundingBox(x1=100, y1=90, x2=140, y2=110))

    def test_clamps_padding_to_image_boundary(self):
        points = [
            (1, 2),
            (5, 8),
        ]

        box = calculate_bounding_box(
            points=points,
            padding=8,
            frame_width=640,
            frame_height=480,
        )

        self.assertEqual(box, BoundingBox(x1=0, y1=0, x2=13, y2=16))

    def test_rejects_empty_points(self):
        with self.assertRaises(ValueError):
            calculate_bounding_box(
                points=[],
                padding=0,
                frame_width=640,
                frame_height=480,
            )


if __name__ == "__main__":
    unittest.main()
