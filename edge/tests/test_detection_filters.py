"""Tests for detection_filters.py — multi-layer false positive reduction."""

import sys
import os
import time
import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from detection_filters import (
    filter_detection, validate_snake, validate_scorpion, validate_person,
    validate_fire, validate_smoke, check_child_heuristic,
    TemporalConfirmer, get_threshold, is_nighttime,
    CLASS_THRESHOLDS, SUPPRESS_CLASSES,
)


class TestSuppressionClasses:
    """Suppression classes (dog, vehicle, bird) should never pass the filter."""

    @pytest.mark.parametrize("cls", ["dog", "vehicle", "bird"])
    def test_suppression_classes_rejected(self, cls, make_detection):
        det = make_detection(cls=cls, confidence=0.99)
        assert filter_detection(det) is None


class TestConfidenceThreshold:
    """Detections below class-specific thresholds are rejected."""

    def test_below_threshold_rejected(self, make_detection):
        det = make_detection(cls="person", confidence=0.10)
        assert filter_detection(det) is None

    def test_above_threshold_passes(self, make_detection):
        det = make_detection(cls="person", confidence=0.85)
        result = filter_detection(det)
        assert result is not None

    def test_snake_higher_threshold(self, make_detection):
        det = make_detection(cls="snake", confidence=0.45,
                             bbox=[100, 300, 300, 450])
        assert filter_detection(det) is None

        det2 = make_detection(cls="snake", confidence=0.55,
                              bbox=[100, 300, 300, 450])
        assert filter_detection(det2) is not None


class TestSnakeValidation:
    """Snake-specific geometric validation."""

    def test_valid_snake_detection(self, make_detection):
        det = make_detection(cls="snake", confidence=0.7,
                             bbox=[100, 350, 400, 420])
        assert validate_snake(det) is True

    def test_square_bbox_rejected(self, make_detection):
        det = make_detection(cls="snake", confidence=0.7,
                             bbox=[100, 300, 200, 400])
        assert validate_snake(det) is False

    def test_too_large_bbox_rejected(self, make_detection):
        det = make_detection(cls="snake", confidence=0.7,
                             bbox=[0, 0, 640, 480])
        assert validate_snake(det) is False

    def test_high_position_rejected(self, make_detection):
        det = make_detection(cls="snake", confidence=0.7,
                             bbox=[100, 10, 400, 50])
        assert validate_snake(det) is False


class TestScorpionValidation:
    """Scorpion-specific geometric validation."""

    def test_valid_scorpion(self, make_detection):
        det = make_detection(cls="scorpion", confidence=0.5,
                             bbox=[200, 400, 230, 430])
        assert validate_scorpion(det) is True

    def test_too_large_rejected(self, make_detection):
        det = make_detection(cls="scorpion", confidence=0.5,
                             bbox=[0, 0, 400, 400])
        assert validate_scorpion(det) is False

    def test_high_position_rejected(self, make_detection):
        det = make_detection(cls="scorpion", confidence=0.5,
                             bbox=[200, 50, 230, 80])
        assert validate_scorpion(det) is False


class TestPersonValidation:
    """Person-specific geometric validation."""

    def test_valid_person(self, make_detection):
        det = make_detection(cls="person", confidence=0.8,
                             bbox=[100, 50, 200, 400])
        assert validate_person(det) is True

    def test_too_small_rejected(self, make_detection):
        det = make_detection(cls="person", confidence=0.8,
                             bbox=[100, 100, 110, 105])
        assert validate_person(det) is False

    def test_too_large_rejected(self, make_detection):
        det = make_detection(cls="person", confidence=0.8,
                             bbox=[0, 0, 640, 478])
        assert validate_person(det) is False


class TestChildHeuristic:
    """Child heuristic flags small persons as possible children."""

    def test_small_person_flagged(self, make_detection):
        det = make_detection(cls="person", confidence=0.8,
                             bbox=[200, 300, 260, 400])
        result = check_child_heuristic(det)
        assert result is True
        assert det.get("metadata", {}).get("possible_child") is True

    def test_tall_person_not_flagged(self, make_detection):
        det = make_detection(cls="person", confidence=0.8,
                             bbox=[200, 50, 300, 400])
        result = check_child_heuristic(det)
        assert result is False


class TestTemporalConfirmer:
    """Temporal confirmation for fire/smoke/scorpion."""

    def test_fire_needs_3_detections(self):
        tc = TemporalConfirmer()
        assert tc.check("cam-01", "fire") is False
        assert tc.check("cam-01", "fire") is False
        assert tc.check("cam-01", "fire") is True

    def test_scorpion_needs_2_detections(self):
        tc = TemporalConfirmer()
        assert tc.check("cam-01", "scorpion") is False
        assert tc.check("cam-01", "scorpion") is True

    def test_person_no_confirmation_needed(self):
        tc = TemporalConfirmer()
        assert tc.check("cam-01", "person") is True

    def test_different_cameras_independent(self):
        tc = TemporalConfirmer()
        tc.check("cam-01", "fire")
        tc.check("cam-01", "fire")
        assert tc.check("cam-02", "fire") is False
        assert tc.check("cam-01", "fire") is True


class TestMasterFilter:
    """Integration tests for the complete filter pipeline."""

    def test_valid_person_passes(self, make_detection):
        det = make_detection(cls="person", confidence=0.85,
                             bbox=[100, 50, 200, 400])
        result = filter_detection(det)
        assert result is not None

    def test_low_confidence_person_rejected(self, make_detection):
        det = make_detection(cls="person", confidence=0.10)
        assert filter_detection(det) is None

    def test_dog_always_rejected(self, make_detection):
        det = make_detection(cls="dog", confidence=0.99)
        assert filter_detection(det) is None

    def test_cow_passes_no_temporal(self, make_detection):
        det = make_detection(cls="cow", confidence=0.85,
                             bbox=[100, 50, 300, 400])
        result = filter_detection(det)
        assert result is not None
