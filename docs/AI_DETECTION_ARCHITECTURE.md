# SudarshanChakra — AI Detection Architecture

## Complete Implementation Guide for Every Detection Subsystem

---

## Table of Contents

1. AI Model Strategy & Class Taxonomy
2. Dataset Engineering & Annotation Pipeline
3. Training Pipeline & Hyperparameter Tuning
4. Detection Subsystem 1: Snake & Scorpion Detection
5. Detection Subsystem 2: Fire & Smoke Detection
6. Detection Subsystem 3: Person Detection & Intruder Classification
7. Detection Subsystem 4: Child Detection (Pond Zero-Tolerance)
8. Detection Subsystem 5: Livestock Containment (Cow Tracking)
9. Virtual Fence Engine — Deep Architecture
10. Multi-Object Tracking (ByteTrack Integration)
11. Night & Low-Light Detection Strategy
12. Edge Inference Optimization (TensorRT Deep Dive)
13. False Positive Reduction Pipeline
14. Model Versioning, A/B Testing & OTA Updates
15. Performance Metrics & Monitoring Dashboard
16. Complete Inference Pipeline Code

---

## 1. AI Model Strategy & Class Taxonomy

### 1.1 Model Architecture Decision

The system uses a **single unified YOLOv8n model** trained on all target classes rather than multiple specialized models. This is critical for the Pi-class RTX 3060 hardware.

**Why single model vs. ensemble:**

| Factor | Single Model | Multi-Model Ensemble |
|:-------|:-------------|:---------------------|
| VRAM usage | ~200 MB | ~800 MB (4 models) |
| Latency per frame | 6-8 ms | 24-32 ms |
| Max cameras at 2.5 FPS | 8 cameras | 2 cameras |
| Training complexity | One dataset, one pipeline | Four separate pipelines |
| Class confusion | Possible (snake vs rope) | Lower per-domain |
| Maintenance burden | Low | High (4× versioning) |

**Decision: Single YOLOv8n** — the RTX 3060 12GB budget demands it when running 8 cameras at 2-3 FPS each. The class confusion risk is managed by post-processing (Section 13).

### 1.2 Class Taxonomy (10 Classes)

```
ID  Class          Parent Category     Priority    Notes
──  ─────          ───────────────     ────────    ─────
0   person         Human               high        Triggers zone checks + LoRa fusion
1   child          Human               critical    Small person (height ratio heuristic)
2   cow            Livestock            warning     Triggers containment check
3   snake          Hazard/Reptile       high        All snake species combined
4   scorpion       Hazard/Arthropod     high        Ground-level detection
5   fire           Hazard/Thermal       critical    Visible flames
6   smoke          Hazard/Thermal       high        Early fire indicator
7   dog            Animal               info        Farm dogs (suppress false alarms)
8   vehicle        Object               info        Car/truck/tractor at gate
9   bird           Animal               info        Suppress false bird triggers
```

**Why 10 classes instead of 7:**
Classes 7-9 (`dog`, `vehicle`, `bird`) are "suppression classes" — they don't trigger alerts themselves but reduce false positives. A dog near the perimeter shouldn't trigger "person detected". A bird flying through frame shouldn't trigger "scorpion detected". Training the model to recognize these explicitly prevents confusion.

### 1.3 Class Priority Mapping

```python
CLASS_PRIORITY = {
    "person":   "high",
    "child":    "critical",
    "cow":      "warning",
    "snake":    "high",
    "scorpion": "high",
    "fire":     "critical",
    "smoke":    "high",
    "dog":      "info",      # No alert — suppression class
    "vehicle":  "info",      # No alert — suppression class
    "bird":     "info",      # No alert — suppression class
}

# Zone type overrides: zero_tolerance zones escalate everything to critical
# regardless of class priority
```

---

## 2. Dataset Engineering & Annotation Pipeline

### 2.1 Dataset Sourcing Strategy

The model requires approximately **15,000-25,000 annotated images** across all classes. Sources are combined from public datasets and on-site collection.

**Source Matrix:**

```
Class       Public Dataset                          On-Site Collection    Target Count
─────       ──────────────                          ──────────────────    ────────────
person      COCO (80K+ persons)                     500 frames            5,000
child       VisDrone, custom subset from COCO        200 frames            1,500
cow         Open Images V7 (cattle subset)          300 frames            2,000
snake       Roboflow "Snake Detection" dataset       100 frames            2,500
            AIcrowd Snake Species dataset
            iNaturalist India reptile exports
scorpion    Roboflow "Scorpion Detection"            50 frames             1,500
            iNaturalist Scorpiones order
fire        FireNet (Dunnings & Breckon)            50 frames             2,000
            Roboflow "Fire Detection v2"
smoke       FIRESENSE dataset                        50 frames             1,500
            Wildfire Smoke Detection (HPWREN)
dog         COCO (dog class), Open Images           100 frames            1,000
vehicle     COCO (car/truck), VisDrone               100 frames            1,000
bird        COCO, iNaturalist                        50 frames             500
```

### 2.2 On-Site Data Collection Protocol

On-site data is critical because public datasets don't represent the specific farm environment (Sanga Reddy lighting, soil color, vegetation, camera angles).

**Collection procedure:**

```
Phase 1: Install cameras and run for 7 days WITHOUT AI
  - Record RTSP streams at 1 FPS to disk (saves ~2 GB/camera/day)
  - Capture across: dawn (5-6 AM), morning (8-10 AM), midday (12-2 PM),
    evening (4-6 PM), dusk (6-7 PM), night (8 PM-5 AM)
  - This gives diverse lighting, shadow, and weather conditions

Phase 2: Curate representative frames
  - Extract every 30th frame → ~200 frames/camera/day
  - Manually select frames with:
    - Workers walking through zones
    - Cattle in/out of pen
    - Various lighting conditions
    - Empty frames (important for training negative examples)
    - Weather variation (rain, mist, clear)

Phase 3: Augmented collection for rare classes
  - Snake: Place rubber snake replicas in camera FOV at various positions
  - Scorpion: Use high-quality printed images at ground level
  - Fire/Smoke: Record controlled small fire/smoke events (supervised)
  - These synthetic placements train the model for the exact camera angles
```

### 2.3 Annotation Pipeline

**Tool: CVAT (Computer Vision Annotation Tool) — self-hosted on Edge Node B**

```bash
# Deploy CVAT on Edge Node B (has GPU for annotation acceleration)
docker compose -f docker-compose-cvat.yml up -d

# Access at: http://10.8.0.11:8080
```

**Annotation standards:**

```
Bounding Box Rules:
  - Box must tightly enclose the VISIBLE portion of the object
  - For partially occluded objects (>30% visible): annotate visible portion
  - For partially occluded objects (<30% visible): skip
  - For person/child: box from head to feet, including extended limbs
  - For snake: box around entire visible body (often S-curved)
  - For fire: box around visible flame area (not glow/reflection)
  - For smoke: box around opaque smoke mass (not transparent wisps)

Label Rules:
  - person: Any human adult or teenager (estimated age >12)
  - child: Any human appearing to be age <12 (see Section 7 for heuristics)
  - cow: Any bovine animal (bulls, calves included)
  - snake: Any snake regardless of species
  - scorpion: Any scorpion
  - fire: Visible flames only (not embers/sparks)
  - smoke: Dense smoke plume (not steam/dust)
  - dog: Any canine (farm dogs, strays)
  - vehicle: Any motorized vehicle (car, truck, tractor, motorcycle)
  - bird: Any bird (crows, egrets, etc.)

Quality Assurance:
  - Every image annotated by 2 annotators independently
  - Disagreements resolved by a third reviewer
  - IoU threshold for agreement: 0.7
  - Target: >95% inter-annotator agreement
```

### 2.4 Dataset Splits & Augmentation

```python
# Dataset split ratios
TRAIN_SPLIT = 0.80    # 80% for training
VAL_SPLIT   = 0.15    # 15% for validation
TEST_SPLIT  = 0.05    # 5% for held-out testing

# Augmentation pipeline (applied during training via Ultralytics config)
augmentation_config = {
    "hsv_h": 0.015,        # Hue shift ±1.5%
    "hsv_s": 0.7,          # Saturation shift ±70%
    "hsv_v": 0.4,          # Value/brightness shift ±40%
    "degrees": 5.0,        # Rotation ±5°
    "translate": 0.1,      # Translation ±10%
    "scale": 0.5,          # Scale ±50%
    "shear": 2.0,          # Shear ±2°
    "perspective": 0.0005, # Slight perspective warp
    "flipud": 0.5,         # Vertical flip 50% (snakes can be any orientation)
    "fliplr": 0.5,         # Horizontal flip 50%
    "mosaic": 1.0,         # Mosaic augmentation 100%
    "mixup": 0.15,         # MixUp augmentation 15%
    "copy_paste": 0.3,     # Copy-paste augmentation 30% (for small objects)
}

# Special augmentations for farm environment:
# - Heavy brightness variation (simulates dawn/dusk/overcast)
# - Rain/fog overlay (using Albumentations if needed)
# - Camera-specific color cast correction
```

### 2.5 YOLO Dataset Directory Structure

```
/datasets/sudarshanchakra_v1/
├── data.yaml
├── train/
│   ├── images/
│   │   ├── frame_cam01_0001.jpg
│   │   ├── frame_cam01_0002.jpg
│   │   └── ...
│   └── labels/
│       ├── frame_cam01_0001.txt    # YOLO format: class x_center y_center w h
│       ├── frame_cam01_0002.txt
│       └── ...
├── val/
│   ├── images/
│   └── labels/
└── test/
    ├── images/
    └── labels/
```

**data.yaml:**

```yaml
# SudarshanChakra YOLOv8 Dataset Config
path: /datasets/sudarshanchakra_v1
train: train/images
val: val/images
test: test/images

nc: 10  # number of classes

names:
  0: person
  1: child
  2: cow
  3: snake
  4: scorpion
  5: fire
  6: smoke
  7: dog
  8: vehicle
  9: bird
```

---

## 3. Training Pipeline & Hyperparameter Tuning

### 3.1 Training Hardware

Training runs on **Edge Node B** (RTX 3060 12GB) when not in production, or on a cloud GPU (A100/V100) for faster iteration.

```bash
# Training command (on Edge Node B or cloud GPU)
yolo detect train \
    model=yolov8n.pt \
    data=/datasets/sudarshanchakra_v1/data.yaml \
    epochs=300 \
    imgsz=640 \
    batch=16 \
    patience=50 \
    device=0 \
    workers=8 \
    project=runs/train \
    name=sc_v1 \
    exist_ok=True \
    # Hyperparameters
    lr0=0.01 \
    lrf=0.01 \
    momentum=0.937 \
    weight_decay=0.0005 \
    warmup_epochs=3.0 \
    warmup_momentum=0.8 \
    warmup_bias_lr=0.1 \
    box=7.5 \
    cls=0.5 \
    dfl=1.5 \
    # Augmentation
    hsv_h=0.015 \
    hsv_s=0.7 \
    hsv_v=0.4 \
    mosaic=1.0 \
    mixup=0.15 \
    copy_paste=0.3
```

### 3.2 Transfer Learning Strategy

```
Phase 1: COCO Pre-trained Base (yolov8n.pt)
  - Start with Ultralytics pre-trained weights
  - Already knows: person, car, truck, dog, bird, cow
  - Needs to learn: snake, scorpion, fire, smoke, child

Phase 2: Full Fine-tune on Farm Dataset
  - Unfreeze ALL layers (not just head)
  - Train for 300 epochs with patience=50 (early stopping)
  - Learning rate: cosine annealing from 0.01 → 0.0001
  - Expected training time on RTX 3060: ~8-12 hours

Phase 3: Farm-Specific Fine-tune
  - After collecting 2 weeks of on-site data with corrections
  - Fine-tune from Phase 2 best weights
  - Lower learning rate: 0.001
  - 100 epochs
  - This corrects for farm-specific false positives
```

### 3.3 Class Imbalance Handling

Snake and scorpion images will be rare compared to person/cow. The dataset needs balancing.

```python
# Class weight strategy
# YOLOv8 handles imbalance internally via focal loss, but we also:

# 1. Oversample rare classes in training data
#    Target ratio: no class should be <5% of total annotations
#    If scorpion has 1500 images out of 20000 total (7.5%) → OK
#    If scorpion has 500 images out of 20000 total (2.5%) → oversample 3×

# 2. Use copy-paste augmentation (copy_paste=0.3)
#    Automatically pastes small object instances into other images
#    Particularly effective for snake/scorpion (small, ground-level)

# 3. Mosaic augmentation (mosaic=1.0)
#    Combines 4 images into one, increasing object density
#    Helps rare classes appear more frequently during training
```

### 3.4 Validation & Acceptance Criteria

```
Minimum mAP@0.5 targets per class:
  person:    > 0.85
  child:     > 0.70  (harder — size-based differentiation)
  cow:       > 0.88
  snake:     > 0.72  (challenging — camouflage, thin shape)
  scorpion:  > 0.65  (very small, challenging)
  fire:      > 0.82
  smoke:     > 0.75
  dog:       > 0.85
  vehicle:   > 0.90
  bird:      > 0.80

Overall mAP@0.5:     > 0.78
Overall mAP@0.5:0.95 > 0.55

Inference speed on RTX 3060 (TensorRT FP16):
  Latency:    < 8 ms per 640×640 frame
  Throughput: > 125 FPS (single stream)
```

---

## 4. Detection Subsystem 1: Snake & Scorpion Detection

### 4.1 Snake Detection — Challenges & Solutions

Snakes are one of the hardest objects to detect in agricultural settings due to camouflage, thin body shape, and similarity to ropes, hoses, and branches.

**Challenge matrix:**

```
Challenge                          Solution
─────────                          ────────
Camouflage (snake matches soil)    Heavy HSV augmentation during training
                                   Low camera mount (1-1.5m) for closer view
                                   Higher confidence threshold for snake (0.50)

Thin elongated shape               YOLOv8 handles aspect ratios well
                                   anchor-free design helps with thin objects
                                   Copy-paste augmentation for small snakes

Confusion with ropes/hoses         Include "rope" and "hose" in training as
                                   negative examples (label as background)
                                   Post-processing temporal filter (Section 13)

Nocturnal movement                 Starlight cameras (Tapo C320WS)
                                   IR-augmented training images (Section 11)

Fast movement (striking)           Frame-skipping at 2 FPS is sufficient
                                   — snakes rarely strike in open ground
                                   Detection is about presence, not action

Species variation                  Single "snake" class for all species
                                   India farm snakes: cobra, krait, rat snake,
                                   vine snake — all trained together
                                   Species ID is NOT required for alert
```

### 4.2 Snake Detection — Camera Placement

```
Camera: TP-Link VIGI C540-W (Pan/Tilt)
Mount height: 1.0 - 1.5 meters (LOW — critical for ground-level detection)
Angle: 45° downward tilt
FOV coverage: ~6m × 4m ground area per camera
FPS: 2.0 (sufficient for slow-moving reptiles)

Placement zones:
  - Rocky/rubble areas (natural habitat)
  - Near water sources (attracted to water)
  - Field boundaries with vegetation
  - Storage shed entrances (mice attract snakes)

Physical deterrents (complement to AI):
  - Keep grass short within camera zones
  - Gravel borders around buildings (snakes avoid open gravel)
  - Vibration devices near high-risk areas
```

### 4.3 Snake Detection — Inference Pipeline Detail

```python
def process_snake_detection(detection: dict, camera_config: dict) -> dict:
    """
    Post-processing specific to snake detections.
    
    Snake detections have a higher false positive rate than other classes.
    Apply additional validation before escalating.
    """
    confidence = detection["confidence"]
    bbox = detection["bbox"]
    x1, y1, x2, y2 = bbox
    
    # 1. Aspect ratio check — snakes are elongated
    width = x2 - x1
    height = y2 - y1
    aspect_ratio = max(width, height) / max(min(width, height), 1)
    
    # Real snakes typically have aspect ratio > 2.5
    # A coiled snake might be ~1.5, but still not square
    if aspect_ratio < 1.3:
        return None  # Too square — likely a false positive
    
    # 2. Size check — reject impossibly large/small detections
    frame_area = 640 * 480  # Approximate frame size
    bbox_area = width * height
    bbox_ratio = bbox_area / frame_area
    
    if bbox_ratio > 0.4:
        return None  # Too large — snake wouldn't fill 40% of frame
    if bbox_ratio < 0.001:
        return None  # Too small — below reliable detection threshold
    
    # 3. Position check — snakes are ground-level
    # Bottom of bbox should be in the lower 70% of the frame
    frame_height = 480
    if y2 < frame_height * 0.3:
        return None  # Detection is too high — snakes don't fly
    
    # 4. Confidence boost for ground-level cameras
    if camera_config.get("mount_height_m", 3.0) < 2.0:
        confidence *= 1.1  # 10% confidence boost for low-mounted cameras
        confidence = min(confidence, 1.0)
    
    detection["confidence"] = confidence
    detection["metadata"] = {
        "aspect_ratio": round(aspect_ratio, 2),
        "bbox_area_ratio": round(bbox_ratio, 4),
        "post_processed": True,
    }
    
    return detection
```

### 4.4 Scorpion Detection — Specific Considerations

Scorpions are even smaller than snakes and present unique challenges.

```
Challenge                          Solution
─────────                          ────────
Very small object (2-8cm)          Low camera mount (1m)
                                   Higher resolution sub-stream if available
                                   SAHI (Slicing Aided Hyper Inference) — see below

UV fluorescence                    Scorpions glow under UV light
                                   Optional: UV LED array + camera for night
                                   (Not standard TP-Link — future upgrade)

Confusion with large insects       Training dataset includes beetles, crickets
                                   as negative examples

Low confidence scores              Lower threshold for scorpion (0.35)
                                   Require 2 consecutive detections to confirm
```

**SAHI Integration for Small Object Detection:**

```python
# SAHI (Slicing Aided Hyper Inference) for scorpion detection
# Splits the frame into overlapping tiles, runs inference on each,
# then merges results with NMS

from sahi import AutoDetectionModel
from sahi.predict import get_sliced_prediction

def detect_with_sahi(frame, model_path, camera_id):
    """
    Use SAHI for cameras in scorpion zones where objects are tiny.
    Only applied to specific cameras — too slow for all 8 cameras.
    """
    detection_model = AutoDetectionModel.from_pretrained(
        model_type="yolov8",
        model_path=model_path,
        confidence_threshold=0.3,
        device="cuda:0",
    )
    
    result = get_sliced_prediction(
        frame,
        detection_model,
        slice_height=320,     # Half of 640
        slice_width=320,
        overlap_height_ratio=0.2,
        overlap_width_ratio=0.2,
        postprocess_type="NMS",
        postprocess_match_threshold=0.5,
    )
    
    return result.object_prediction_list

# NOTE: SAHI adds ~40ms latency per frame. Only enable for
# cameras specifically pointed at scorpion zones.
# Set in cameras.json: "sahi_enabled": true
```

---

## 5. Detection Subsystem 2: Fire & Smoke Detection

### 5.1 Dual-Stage Fire Detection Architecture

Fire detection uses a **dual-stage approach**: primary YOLO detection plus secondary color-histogram validation to reduce false positives.

```
Stage 1: YOLO Detection
  ├── Detects: "fire" and "smoke" classes
  ├── Confidence threshold: fire=0.45, smoke=0.40
  └── Output: bounding box + confidence

Stage 2: Color Histogram Validation (fire only)
  ├── Extract pixels inside bounding box
  ├── Convert to HSV color space
  ├── Check: does color histogram match fire signature?
  │   Fire HSV ranges:
  │     H: 0-30 (red-orange-yellow)
  │     S: 100-255 (saturated)
  │     V: 200-255 (bright)
  ├── Pixel ratio: >30% of bbox pixels must be in fire range
  └── If yes → CONFIRMED fire. If no → reject as false positive.
```

```python
import cv2
import numpy as np

def validate_fire_detection(frame: np.ndarray, bbox: list) -> dict:
    """
    Secondary validation for fire detections using color analysis.
    Reduces false positives from: orange clothing, sunset glow,
    reflections, red vehicles, etc.
    """
    x1, y1, x2, y2 = [int(v) for v in bbox]
    
    # Clamp to frame boundaries
    h, w = frame.shape[:2]
    x1, y1 = max(0, x1), max(0, y1)
    x2, y2 = min(w, x2), min(h, y2)
    
    # Extract ROI
    roi = frame[y1:y2, x1:x2]
    if roi.size == 0:
        return {"valid": False, "reason": "empty_roi"}
    
    # Convert to HSV
    hsv = cv2.cvtColor(roi, cv2.COLOR_BGR2HSV)
    
    # Fire color ranges in HSV
    # Range 1: Red-Orange (H: 0-15)
    lower1 = np.array([0, 100, 200])
    upper1 = np.array([15, 255, 255])
    mask1 = cv2.inRange(hsv, lower1, upper1)
    
    # Range 2: Orange-Yellow (H: 15-35)
    lower2 = np.array([15, 100, 200])
    upper2 = np.array([35, 255, 255])
    mask2 = cv2.inRange(hsv, lower2, upper2)
    
    # Range 3: Deep Red wrap-around (H: 170-180)
    lower3 = np.array([170, 100, 200])
    upper3 = np.array([180, 255, 255])
    mask3 = cv2.inRange(hsv, lower3, upper3)
    
    # Combine masks
    fire_mask = mask1 | mask2 | mask3
    
    # Calculate fire pixel ratio
    total_pixels = roi.shape[0] * roi.shape[1]
    fire_pixels = cv2.countNonZero(fire_mask)
    fire_ratio = fire_pixels / total_pixels
    
    # Flickering analysis (optional — needs 2+ consecutive frames)
    # Fire flickers with high-frequency intensity variation
    
    return {
        "valid": fire_ratio > 0.30,  # >30% fire-colored pixels
        "fire_pixel_ratio": round(fire_ratio, 3),
        "total_pixels": total_pixels,
        "fire_pixels": fire_pixels,
        "reason": "color_confirmed" if fire_ratio > 0.30 else "insufficient_fire_color",
    }


def validate_smoke_detection(frame: np.ndarray, bbox: list) -> dict:
    """
    Secondary validation for smoke detections.
    Smoke is gray/white with low saturation and medium-high value.
    Reduces false positives from: clouds, dust, fog, mist.
    """
    x1, y1, x2, y2 = [int(v) for v in bbox]
    h, w = frame.shape[:2]
    x1, y1 = max(0, x1), max(0, y1)
    x2, y2 = min(w, x2), min(h, y2)
    
    roi = frame[y1:y2, x1:x2]
    if roi.size == 0:
        return {"valid": False, "reason": "empty_roi"}
    
    hsv = cv2.cvtColor(roi, cv2.COLOR_BGR2HSV)
    
    # Smoke HSV: low saturation (grayish), medium-high value
    lower = np.array([0, 0, 80])
    upper = np.array([180, 80, 220])
    smoke_mask = cv2.inRange(hsv, lower, upper)
    
    total_pixels = roi.shape[0] * roi.shape[1]
    smoke_pixels = cv2.countNonZero(smoke_mask)
    smoke_ratio = smoke_pixels / total_pixels
    
    # Texture analysis — smoke has uniform texture (low variance)
    gray_roi = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
    texture_variance = float(np.var(gray_roi))
    
    # Smoke: high smoke_ratio AND low-medium texture variance
    # Clouds: high smoke_ratio BUT very low variance (more uniform)
    # Trees: low smoke_ratio AND high variance
    is_smoke = smoke_ratio > 0.40 and 100 < texture_variance < 3000
    
    return {
        "valid": is_smoke,
        "smoke_pixel_ratio": round(smoke_ratio, 3),
        "texture_variance": round(texture_variance, 1),
        "reason": "smoke_confirmed" if is_smoke else "failed_validation",
    }
```

### 5.2 Fire Detection — Temporal Confirmation

Single-frame fire detection has a high false positive rate. The system requires **3 consecutive detections within 5 seconds** to confirm a fire event.

```python
from collections import defaultdict
import time

class TemporalConfirmer:
    """
    Requires N consecutive detections within T seconds to confirm.
    Used for fire and smoke to reduce single-frame false positives.
    """
    
    def __init__(self, required_count: int = 3, window_seconds: float = 5.0):
        self.required_count = required_count
        self.window_seconds = window_seconds
        self._history = defaultdict(list)  # "camera:class" → [timestamps]
    
    def check(self, camera_id: str, detection_class: str) -> bool:
        """Returns True if detection is temporally confirmed."""
        key = f"{camera_id}:{detection_class}"
        now = time.time()
        
        # Add current detection
        self._history[key].append(now)
        
        # Remove old entries
        cutoff = now - self.window_seconds
        self._history[key] = [t for t in self._history[key] if t > cutoff]
        
        # Check if enough detections in window
        return len(self._history[key]) >= self.required_count
    
    def reset(self, camera_id: str, detection_class: str):
        """Reset history after alert is sent."""
        key = f"{camera_id}:{detection_class}"
        self._history[key] = []

# Usage in alert_engine.py:
fire_confirmer = TemporalConfirmer(required_count=3, window_seconds=5.0)

def process_detection(detection):
    if detection["class"] in ("fire", "smoke"):
        if not fire_confirmer.check(detection["camera_id"], detection["class"]):
            return  # Not yet confirmed — wait for more frames
        # Confirmed! Continue to zone check and alert...
```

---

## 6. Detection Subsystem 3: Person Detection & Intruder Classification

### 6.1 Person Detection Architecture

Person detection is the most critical class — it triggers virtual fencing, LoRa sensor fusion, and intrusion alerts.

```
Detection Pipeline for "person":

    YOLO detects "person"
         │
         ▼
    Bottom-center (feet) extracted
         │
         ▼
    Zone engine: is feet-point inside any polygon?
         │
    ┌────┴────────────────────────────────────────────┐
    │ NO                                               │ YES
    │ Not in any zone                                  │
    │ → Ignore                                         │
    │                                                  ▼
    │                                        Zone type check
    │                                   ┌──────┴──────────┐
    │                              zero_tolerance     intrusion/hazard
    │                                   │                  │
    │                                   │                  ▼
    │                                   │           LoRa fusion:
    │                                   │           worker nearby?
    │                                   │          ┌────┴──────┐
    │                                   │         YES          NO
    │                                   │          │            │
    │                                   │     SUPPRESS      ALERT!
    │                                   │     (log only)   "intruder_detected"
    │                                   │                      │
    │                                   ▼                      ▼
    │                              ALERT!              MQTT publish
    │                         (NEVER suppressed)    farm/alerts/high
    │                              │
    │                              ▼
    │                         MQTT publish
    │                      farm/alerts/critical
    └──────────────────────────────────────────────────────────┘
```

### 6.2 Feet Position (Bottom-Center) Calculation

The virtual fence checks where a person is **standing**, not where their head is. This requires using the bottom-center of the bounding box as the test point.

```python
def get_feet_position(bbox: list) -> tuple:
    """
    Calculate the bottom-center point of a bounding box.
    This represents where the person is standing (feet position).
    
    bbox: [x1, y1, x2, y2] in pixel coordinates
    
    Returns: (feet_x, feet_y)
    
    Visual:
        ┌──────────┐
        │  person   │
        │   head    │ ← y1
        │   body    │
        │   legs    │
        └────●─────┘ ← y2 (bottom)
             ↑
           feet_x = (x1 + x2) / 2
           feet_y = y2
    """
    x1, y1, x2, y2 = bbox
    feet_x = (x1 + x2) / 2.0   # Horizontal center
    feet_y = y2                  # Bottom edge = ground contact
    return (feet_x, feet_y)

# Why bottom-center and not bbox center?
# 
# Consider a person standing at the edge of the pond zone:
#   - Their HEAD might be outside the polygon (leaning forward)
#   - Their FEET are inside the polygon (standing in the zone)
#   - Using bbox center would miss this critical case
# 
# The bottom-center is the most reliable indicator of where
# a person physically IS in the scene.
```

### 6.3 LoRa Sensor Fusion — Detailed Flow

```
                    ┌───────────────────────────┐
                    │  Edge Node (i5-10400)      │
                    │                            │
   ┌────────┐      │  ┌──────────────────────┐  │
   │ Worker │ LoRa  │  │ ESP32 LoRa Bridge    │  │
   │ Tag    │──────▶│  │ (USB-Serial)         │  │
   │ ESP32  │ 433MHz│  │                      │  │
   └────────┘      │  │ TAG:W001,TYPE:PING,  │  │
                    │  │ RSSI:-42             │  │
                    │  └──────────┬───────────┘  │
                    │             │ USB Serial   │
                    │  ┌──────────▼───────────┐  │
                    │  │ lora_receiver.py     │  │
                    │  │ last_seen = {        │  │
                    │  │   "TAG-W001": {      │  │
                    │  │     ts: 1709712345,  │  │
                    │  │     rssi: -42        │  │
                    │  │   }                  │  │
                    │  │ }                    │  │
                    │  └──────────┬───────────┘  │
                    │             │               │
                    │  ┌──────────▼───────────┐  │
                    │  │ alert_engine.py      │  │
                    │  │                      │  │
                    │  │ "Person detected"    │  │
                    │  │   → is_worker_nearby?│  │
                    │  │   → TAG-W001 seen 3s │  │
                    │  │     ago (< 15s max)  │  │
                    │  │   → SUPPRESS alarm   │  │
                    │  │   → Log to           │  │
                    │  │     worker.suppression│  │
                    │  └──────────────────────┘  │
                    └───────────────────────────┘

Timing diagram:
  t=0.000s  Worker walks into perimeter zone
  t=0.400s  Camera grabs frame (2.5 FPS)
  t=0.406s  YOLO detects "person" (6ms inference)
  t=0.407s  Zone engine: person is INSIDE perimeter zone
  t=0.408s  LoRa check: TAG-W001 last seen 2.3s ago → WITHIN 15s window
  t=0.408s  Decision: SUPPRESS (authorized worker)
  t=0.409s  Log suppression event to MQTT (QoS 0)
  
  Total decision latency: ~9ms from frame capture to decision
  
If NO tag is detected:
  t=0.408s  LoRa check: no authorized tags in last 15s
  t=0.408s  Decision: ALERT (intruder!)
  t=0.410s  Publish to farm/alerts/high (QoS 1)
  t=0.500s  VPS receives alert via MQTT over VPN
  t=0.600s  Alert service stores in PostgreSQL
  t=0.700s  FCM push notification sent to Android app
  t=1.500s  Android notification appears on screen
  
  Total end-to-end latency: ~1.5 seconds from detection to phone buzz
```

---

## 7. Detection Subsystem 4: Child Detection (Pond Zero-Tolerance)

### 7.1 Child vs Adult Classification

YOLOv8 treats `child` and `person` as separate classes. The model learns to distinguish children by:

```
Visual cues the model learns:
  1. Absolute height ratio  — child bounding box is shorter relative to frame
  2. Head-to-body ratio    — children have proportionally larger heads
  3. Proportions           — shorter limbs, rounder body shape
  4. Context               — often near adults, lower to ground
  5. Clothing patterns     — (not reliable but adds signal)
```

**Post-processing heuristic for edge cases:**

```python
def refine_child_detection(detection: dict, frame_height: int) -> str:
    """
    When model outputs "person" with moderate confidence,
    apply heuristics to check if it might be a child.
    
    This catches cases where the model is uncertain between
    person (adult) and child classes.
    """
    bbox = detection["bbox"]
    x1, y1, x2, y2 = bbox
    
    bbox_height = y2 - y1
    bbox_width = x2 - x1
    
    # Height ratio: child bbox is typically <35% of frame height
    # at standard camera distances (3-4m mount)
    height_ratio = bbox_height / frame_height
    
    # Aspect ratio: children are more "square" (shorter, wider proportionally)
    aspect = bbox_height / max(bbox_width, 1)
    
    # If model said "person" but bbox is small and squat,
    # it might be a child — escalate to critical for zero-tolerance zones
    if detection["class"] == "person":
        if height_ratio < 0.30 and aspect < 2.0:
            detection["metadata"]["possible_child"] = True
            detection["metadata"]["height_ratio"] = round(height_ratio, 3)
            # In zero-tolerance zones, treat ambiguous small persons as children
            return "possible_child"
    
    return detection["class"]
```

### 7.2 Zero-Tolerance Pond Safety — Complete System

The pond safety system is **dual-path** — either the AI camera OR the ESP32 fall detector can independently trigger a CRITICAL alert.

```
PATH 1: Visual Detection (Camera AI)
  Camera (VIGI C540-W) pointed at pond, running at 3 FPS
    │
    ▼
  YOLO detects person/child in frame
    │
    ▼
  Zone engine: is feet-point inside pond zone polygon?
    │
    ▼
  Zone type = "zero_tolerance"
    │
    ▼
  SKIP LoRa fusion (zero_tolerance NEVER suppressed)
    │
    ▼
  CRITICAL alert published immediately
    │
    ▼
  farm/alerts/critical → VPS → FCM → Android phone
  Simultaneously: farm/siren/trigger → Edge Node → PA system


PATH 2: Fall Detection (ESP32 Wearable)
  Child wears ESP32+MPU6050 on wrist/ankle
    │
  Accelerometer samples at 20 Hz
    │
  Fall detection algorithm:
    Phase 1: Free-fall (accel < 0.3g for >150ms)
    Phase 2: Impact (accel > 2.5g within 500ms)
    │
  Both phases detected → FALL CONFIRMED
    │
  ESP32 transmits FALL packet 3× via LoRa
    │
  LoRa bridge receiver → USB → lora_receiver.py
    │
  fall_callback → alert_engine.process_fall_event()
    │
  CRITICAL alert published immediately
    │
  farm/alerts/critical → VPS → FCM → Android phone


BOTH PATHS ARE INDEPENDENT:
  - If camera is occluded (fog, rain) → fall detector still works
  - If child removes wearable → camera still detects
  - If child runs out of camera FOV → fall detector still works within LoRa range
  - REDUNDANCY IS THE POINT
```

---

## 8. Detection Subsystem 5: Livestock Containment

### 8.1 Cow Containment — Inverse Zone Logic

Unlike intrusion detection (alert when INSIDE zone), livestock containment triggers when a cow is detected OUTSIDE the designated area.

```python
# zone_engine.py — livestock containment logic

# For "intrusion" zones:
#   person INSIDE polygon → ALERT
#
# For "livestock_containment" zones:
#   cow INSIDE polygon → OK (cow is where it should be)
#   cow OUTSIDE polygon → ALERT (cow has escaped)

# The polygon defines where the cow SHOULD be (the pen/pasture)
# Detection outside this polygon means containment breach
```

### 8.2 Cow Tracking with ByteTrack

To avoid alerting every frame when a cow is near the boundary, the system uses **ByteTrack** for multi-object tracking with track IDs.

```python
# ByteTrack integration for persistent cow tracking
# Each cow gets a unique track_id that persists across frames

from collections import defaultdict
import time

class LivestockTracker:
    """
    Tracks individual cows across frames and manages containment alerts.
    
    Without tracking: 
      Cow at boundary → alert every 0.4s (2.5 FPS) = flood of alerts
    
    With tracking:
      Cow #3 exits pen → ONE alert for cow #3
      Cow #3 returns to pen → auto-resolve alert
      Cow #3 exits again → new alert (only after 5-min cooldown)
    """
    
    def __init__(self):
        self.cow_states = {}  # track_id → {"inside": bool, "last_alert": timestamp}
        self.COOLDOWN = 300   # 5 minutes between re-alerts for same cow
    
    def update(self, track_id: int, is_inside_pen: bool, zone_id: str) -> str:
        """
        Update cow state and decide whether to alert.
        
        Returns: "alert" | "resolve" | "no_change"
        """
        now = time.time()
        key = f"{track_id}:{zone_id}"
        
        prev_state = self.cow_states.get(key, {"inside": True, "last_alert": 0})
        
        if not is_inside_pen and prev_state["inside"]:
            # Cow just exited the pen
            if now - prev_state["last_alert"] > self.COOLDOWN:
                self.cow_states[key] = {"inside": False, "last_alert": now}
                return "alert"  # → Trigger containment breach alert
        
        elif is_inside_pen and not prev_state["inside"]:
            # Cow returned to pen
            self.cow_states[key] = {"inside": True, "last_alert": prev_state["last_alert"]}
            return "resolve"  # → Auto-resolve the alert
        
        self.cow_states[key] = {
            "inside": is_inside_pen,
            "last_alert": prev_state["last_alert"],
        }
        return "no_change"
```

---

## 9. Virtual Fence Engine — Deep Architecture

### 9.1 Point-in-Polygon Algorithm

The zone engine uses **Shapely** which implements the **ray casting algorithm** (also called the even-odd rule).

```
Ray Casting Algorithm:
  1. Cast a horizontal ray from the test point to infinity
  2. Count how many polygon edges the ray crosses
  3. If odd → point is INSIDE
  4. If even → point is OUTSIDE

Visual example:
  
  ┌─────────────────────┐
  │   Polygon Zone       │
  │                      │
  │        ●──────────── │ ──→ Ray crosses 1 edge (ODD → INSIDE)
  │                      │
  │                      │
  └─────────────────────┘
  
             ●────────── ──→ Ray crosses 0 edges (EVEN → OUTSIDE)


Edge cases handled by Shapely:
  - Point exactly on edge → considered INSIDE (boundary inclusion)
  - Ray passes through vertex → handled by perturbation
  - Concave polygons → algorithm works correctly
  - Self-intersecting polygons → undefined (don't draw these)
```

### 9.2 Coordinate System & Calibration

```
Camera Frame Coordinates:
  Origin (0,0) is top-left
  X increases rightward
  Y increases downward
  
  (0,0)──────────────────(640,0)
  │                           │
  │      Camera View          │
  │                           │
  │                           │
  (0,480)────────────────(640,480)

Zone polygon coordinates are in this PIXEL space.
When a person is detected:
  1. YOLO outputs bbox in pixel coords: [x1, y1, x2, y2]
  2. Feet position: ((x1+x2)/2, y2) — also in pixel coords
  3. Shapely checks if this point is inside the polygon — also in pixel coords

No camera calibration or perspective correction is needed because
both the polygon and the detection are in the same pixel space.

IMPORTANT: If a camera is moved or zoom changes, zones MUST be redrawn
via the Edge GUI. The polygon coordinates are camera-view-specific.
```

### 9.3 Multi-Zone Overlap Handling

```python
def check_all_zones(detection: dict, zones: list) -> list:
    """
    A single detection can violate MULTIPLE zones simultaneously.
    
    Example: Person near the pond is inside BOTH:
      - "Pond Danger Zone" (zero_tolerance, critical)
      - "Farm Perimeter" (intrusion, high)
    
    The system generates ONE alert using the HIGHEST priority zone.
    """
    violations = []
    
    for zone in zones:
        if check_point_in_zone(detection, zone):
            violations.append(zone)
    
    if not violations:
        return []
    
    # Sort by priority: critical > high > warning
    priority_order = {"critical": 0, "high": 1, "warning": 2, "info": 3}
    violations.sort(key=lambda z: priority_order.get(z["priority"], 99))
    
    # Return only the highest-priority violation to avoid duplicate alerts
    return [violations[0]]
```

---

## 10. Multi-Object Tracking (ByteTrack Integration)

### 10.1 Why Tracking Matters

Without tracking, every frame generates independent detections. This causes:
- Alert flooding (same person detected 2.5 times/second)
- No way to count unique intruders
- No way to track movement path
- No way to correlate cow exit/return events

**ByteTrack** assigns persistent track IDs that survive across frames, even through brief occlusions.

```python
# ByteTrack integration (optional, adds ~2ms per frame)
# Install: pip install bytetracker

from bytetracker import BYTETracker
import numpy as np

class ObjectTracker:
    """
    Wraps ByteTrack for persistent object tracking across frames.
    
    Each detected object gets a track_id that persists as long as
    the object is visible (and briefly through occlusions).
    """
    
    def __init__(self):
        self.tracker = BYTETracker(
            track_thresh=0.4,    # Detection confidence threshold
            track_buffer=30,     # Frames to keep lost tracks (30 frames = ~12s at 2.5 FPS)
            match_thresh=0.8,    # IoU threshold for matching
            frame_rate=3,        # Approximate FPS
        )
    
    def update(self, detections: list, frame_shape: tuple) -> list:
        """
        Update tracker with new detections and return tracked objects.
        
        Input: list of detections with bbox and confidence
        Output: same detections with added "track_id" field
        """
        if not detections:
            return []
        
        # Convert to ByteTrack format: [x1, y1, x2, y2, confidence]
        dets = np.array([
            [*d["bbox"], d["confidence"]] for d in detections
        ])
        
        # Run tracker
        tracks = self.tracker.update(dets, frame_shape[:2], frame_shape[:2])
        
        # Map tracks back to detections
        tracked = []
        for track in tracks:
            # Find closest detection by IoU
            for det in detections:
                iou = compute_iou(track.tlbr, det["bbox"])
                if iou > 0.5:
                    det["track_id"] = int(track.track_id)
                    tracked.append(det)
                    break
        
        return tracked
```

---

## 11. Night & Low-Light Detection Strategy

### 11.1 Camera Capabilities

```
Camera         Night Mode              IR Range    Starlight
──────         ──────────              ────────    ─────────
VIGI C540-W    IR LEDs (850nm)         30m         No
Tapo C210      IR LEDs (850nm)         10m         No
Tapo C320WS    IR LEDs (850nm)         30m         Yes (color night at 0.04 lux)
```

### 11.2 IR Image Training

Night-mode cameras produce grayscale IR images. The model MUST be trained on both color AND IR images.

```python
# Training data augmentation for night/IR simulation
import cv2
import numpy as np

def simulate_ir_image(color_frame: np.ndarray) -> np.ndarray:
    """
    Convert a color training image to simulated IR grayscale.
    This augments the training set so the model recognizes
    objects in both day (color) and night (IR/grayscale) modes.
    """
    # Convert to grayscale
    gray = cv2.cvtColor(color_frame, cv2.COLOR_BGR2GRAY)
    
    # Add IR-like characteristics:
    # 1. Slight green tint (IR cameras often have green cast)
    # 2. Reduced contrast in shadows
    # 3. Hot spots glow (simulate IR reflection)
    
    # Apply CLAHE for IR-like contrast
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    enhanced = clahe.apply(gray)
    
    # Convert back to 3-channel (YOLO expects 3 channels)
    ir_frame = cv2.cvtColor(enhanced, cv2.COLOR_GRAY2BGR)
    
    # Optional: add slight noise (IR cameras are noisier)
    noise = np.random.normal(0, 5, ir_frame.shape).astype(np.uint8)
    ir_frame = cv2.add(ir_frame, noise)
    
    return ir_frame

# During dataset preparation:
# For every daytime training image, generate an IR variant
# This doubles the effective dataset size for night scenarios
```

### 11.3 Night-Specific Detection Adjustments

```python
NIGHT_CONFIG = {
    # Lower confidence thresholds at night (objects are harder to see)
    "confidence_adjustments": {
        "person": -0.05,     # 0.40 → 0.35
        "child":  -0.05,     # 0.40 → 0.35  
        "snake":  -0.10,     # 0.50 → 0.40 (snakes harder in IR)
        "scorpion": -0.10,   # 0.35 → 0.25
        "fire":   +0.00,     # Fire is BRIGHTER at night (no adjustment)
        "smoke":  -0.15,     # Smoke nearly invisible in IR
        "cow":    -0.05,     # 0.40 → 0.35
    },
    
    # Night hours (Sanga Reddy, India: ~18:30 to ~05:30 year-round)
    "night_start_hour": 18,
    "night_end_hour": 6,
    
    # Increase temporal confirmation for fire/smoke at night
    # (more false positives from headlights, flashlights, stars)
    "fire_confirm_count": 5,    # 3 → 5 frames at night
}

def is_nighttime() -> bool:
    """Check if current time is during night hours."""
    from datetime import datetime
    hour = datetime.now().hour
    return hour >= NIGHT_CONFIG["night_start_hour"] or hour < NIGHT_CONFIG["night_end_hour"]

def get_adjusted_threshold(class_name: str, base_threshold: float) -> float:
    """Adjust detection threshold based on time of day."""
    if is_nighttime():
        adj = NIGHT_CONFIG["confidence_adjustments"].get(class_name, 0)
        return max(0.15, base_threshold + adj)  # Never go below 0.15
    return base_threshold
```

---

## 12. Edge Inference Optimization (TensorRT Deep Dive)

### 12.1 TensorRT Export Pipeline

```python
from ultralytics import YOLO

def export_tensorrt(pt_path: str, engine_path: str):
    """
    Export YOLOv8n to TensorRT FP16 engine.
    
    Performance on RTX 3060 12GB:
      PyTorch FP32:  ~15ms/frame (67 FPS)
      ONNX FP32:     ~12ms/frame (83 FPS)
      TensorRT FP32: ~8ms/frame  (125 FPS)
      TensorRT FP16: ~6ms/frame  (167 FPS)  ← We use this
      TensorRT INT8: ~4ms/frame  (250 FPS)  ← Not used (accuracy drop)
    
    FP16 gives 2.5× speedup over PyTorch with <0.5% mAP loss.
    INT8 gives 3.75× speedup but ~2-3% mAP loss — not worth it
    for safety-critical detection.
    """
    model = YOLO(pt_path)
    model.export(
        format="engine",
        half=True,           # FP16 precision
        device=0,            # GPU 0
        workspace=4,         # 4GB workspace for optimization
        simplify=True,       # ONNX simplification before TensorRT
        dynamic=False,       # Static input shape (640×640)
        batch=1,             # Single-image batch (we process one at a time)
    )
    # Engine is saved alongside the .pt file with .engine extension

# GPU Memory Budget (RTX 3060 12GB):
#   TensorRT engine loaded:    ~200 MB
#   CUDA context:              ~300 MB
#   Frame buffers (8 cameras): ~100 MB
#   Workspace:                 ~500 MB
#   ──────────────────────────────────
#   Total:                    ~1,100 MB out of 12,288 MB
#   Headroom:                 ~11 GB (91% free)
```

### 12.2 Throughput Calculation

```
Given:
  8 cameras × 2.5 FPS = 20 inferences/second
  TensorRT FP16 latency = 6ms/inference
  
GPU time per second:
  20 × 6ms = 120ms out of 1000ms = 12% GPU utilization

This means the RTX 3060 is massively underutilized at 12%.
Headroom for:
  - Increasing to 5 FPS per camera (24% utilization)
  - Adding more cameras (up to 40 cameras theoretically)
  - Running SAHI for scorpion zones (adds ~40ms per frame)
  - Future model upgrades (YOLOv8s = ~10ms, still comfortable)
```

---

## 13. False Positive Reduction Pipeline

### 13.1 Multi-Layer Filtering

Every detection passes through 5 filters before becoming an alert:

```
Layer 1: YOLO Confidence Filter
  └── Reject detections below class-specific threshold
      person: 0.40, snake: 0.50, scorpion: 0.35, fire: 0.45, smoke: 0.40

Layer 2: Geometric Validation
  └── Class-specific shape/size/position checks
      snake: aspect ratio > 1.3, ground-level position
      scorpion: very small bbox, ground-level
      fire: color histogram validation (Section 5)
      person: reasonable height (10-80% of frame)

Layer 3: Temporal Confirmation
  └── fire/smoke: require 3+ detections in 5 seconds
      scorpion: require 2+ detections in 3 seconds
      snake: no temporal requirement (may pass quickly)
      person: no temporal requirement (instant response needed)

Layer 4: Zone Relevance
  └── Detection must be inside a monitored zone polygon
      No zone match → detection is discarded

Layer 5: Deduplication
  └── Same zone + same class cannot re-trigger within 30 seconds
      Prevents alert flooding from continuous detections
```

### 13.2 Confusion Matrix — Known False Positive Sources

```
Detection     Common False Positives           Mitigation
─────────     ─────────────────────            ──────────
snake         Rope, hose, branch, shadow       Aspect ratio filter + temporal
scorpion      Large beetle, leaf, debris       2-frame temporal confirmation
fire          Sunset glow, orange clothing,    Color histogram validation
              red vehicle, reflections
smoke         Clouds, dust, fog, mist          Texture variance analysis
person        Scarecrow, mannequin, poster     LoRa fusion (no tag = alert)
child         Short adult, person far away     Height ratio heuristic
cow           Horse, buffalo, large dog        Training dataset includes these
                                               as separate classes
```

---

## 14. Model Versioning, A/B Testing & OTA Updates

### 14.1 Model Version Management

```
/app/models/
├── yolov8n_farm_v1.0.engine     # Production model
├── yolov8n_farm_v1.0.pt         # Source weights
├── yolov8n_farm_v1.1.engine     # Candidate model (A/B testing)
├── yolov8n_farm_v1.1.pt
├── model_config.json            # Active model pointer
└── metrics/
    ├── v1.0_metrics.json        # mAP, precision, recall per class
    └── v1.1_metrics.json
```

**model_config.json:**
```json
{
  "active_model": "yolov8n_farm_v1.0.engine",
  "candidate_model": "yolov8n_farm_v1.1.engine",
  "ab_test_enabled": false,
  "ab_split_ratio": 0.1,
  "min_metrics": {
    "mAP50": 0.78,
    "person_precision": 0.85,
    "snake_recall": 0.70
  }
}
```

### 14.2 OTA Model Update Flow

```
1. Train new model on updated dataset (Edge Node B or cloud)
2. Export to TensorRT on Edge Node B (same GPU as production)
3. Run validation suite: metrics must meet minimum thresholds
4. Push model file to Edge Node A via rsync over VPN:
     rsync -av --progress model_v1.1.engine 10.8.0.10:/app/models/
5. Send MQTT command to switch model:
     farm/admin/model_update {"model": "yolov8n_farm_v1.1.engine"}
6. Edge node loads new model, runs health check
7. If health check passes: publish model_updated event
8. If health check fails: rollback to previous model automatically
```

---

## 15. Performance Metrics & Monitoring

### 15.1 Inference Metrics Published via MQTT

```python
# Published every 30 seconds in heartbeat message
inference_metrics = {
    "model_version": "v1.0",
    "avg_inference_ms": 6.2,
    "max_inference_ms": 11.4,
    "frames_processed": 14523,
    "detections_total": 847,
    "detections_by_class": {
        "person": 312,
        "cow": 245,
        "snake": 3,
        "fire": 0,
        "dog": 187,
        "bird": 100,
    },
    "alerts_published": 23,
    "worker_suppressions": 289,
    "false_positive_marked": 7,
    "cameras_connected": 8,
    "gpu_utilization_pct": 12.3,
    "gpu_memory_mb": 1100,
    "gpu_temp_c": 52,
    "queue_depth": 2,
}
```

---

## 16. Complete Inference Pipeline Code

The complete detection pipeline integrating all subsystems is already implemented across these files:

```
farm_edge_node.py    — Main orchestrator, wires everything together
pipeline.py          — Multi-camera RTSP grabbing + YOLO inference
zone_engine.py       — Virtual fence polygon checks (all zone types)
lora_receiver.py     — ESP32 LoRa tag reception + fall detection
alert_engine.py      — Central decision engine (fusion + dedup + publish)
edge_gui.py          — Flask web GUI for polygon drawing
```

The architecture document you're reading now (AI_DETECTION_ARCHITECTURE.md) provides the deep technical detail behind every decision in those files. Together, they form the complete AI detection system for the SudarshanChakra Smart Farm.
