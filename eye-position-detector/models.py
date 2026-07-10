from __future__ import annotations

from dataclasses import asdict, dataclass


Point = tuple[int, int]


@dataclass(frozen=True)
class BoundingBox:
    x1: int
    y1: int
    x2: int
    y2: int

    def as_tuple(self) -> tuple[int, int, int, int]:
        return self.x1, self.y1, self.x2, self.y2


@dataclass(frozen=True)
class EyeDetection:
    label: str
    points: list[Point]
    box: BoundingBox


@dataclass(frozen=True)
class FaceEyeDetection:
    face_id: int
    left_eye: EyeDetection
    right_eye: EyeDetection

    def to_dict(self) -> dict:
        return asdict(self)
