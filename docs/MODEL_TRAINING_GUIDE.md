# SudarshanChakra — YOLOv8 Custom Model Training Guide

> Step-by-step guide to train, validate, export, and deploy a custom YOLOv8n model for the SudarshanChakra smart farm hazard detection system.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Prerequisites](#2-prerequisites)
3. [Class Taxonomy](#3-class-taxonomy)
4. [Dataset Acquisition](#4-dataset-acquisition)
5. [On-Site Data Collection](#5-on-site-data-collection)
6. [Annotation with CVAT](#6-annotation-with-cvat)
7. [Dataset Preparation](#7-dataset-preparation)
8. [Training](#8-training)
9. [Validation & Acceptance Criteria](#9-validation--acceptance-criteria)
10. [TensorRT Export](#10-tensorrt-export)
11. [Deployment to Edge Nodes](#11-deployment-to-edge-nodes)
12. [Retraining & Continuous Improvement](#12-retraining--continuous-improvement)
13. [Troubleshooting](#13-troubleshooting)
14. [Quick Reference](#14-quick-reference)

---

## 1. Overview

### What We Are Training

A single **YOLOv8n** (nano) object detection model that recognizes 10 classes relevant to farm security. The model runs on NVIDIA Jetson Orin Nano or RTX 3060 edge hardware, processing 8 camera feeds at 2–3 FPS each.

### Why a Custom Model?

The stock YOLOv8n ships pre-trained on the COCO dataset (80 general-purpose classes). It already knows `person`, `car`, `dog`, `bird`, and `cow`, but it has **no knowledge of**:

| Missing Class | Why It Matters |
|:---|:---|
| snake | Farm hazard — must detect all species (cobra, krait, rat snake) |
| scorpion | Farm hazard — very small, ground-level |
| fire | Critical safety — visible flames |
| smoke | Early fire indicator |
| child | Pond zero-tolerance safety — distinct from adult |

Training a custom model teaches YOLOv8n these farm-specific classes while preserving its existing knowledge through **transfer learning**.

### Why a Single Model (Not an Ensemble)?

| Factor | Single Model | Multi-Model Ensemble |
|:---|:---|:---|
| VRAM usage | ~200 MB | ~800 MB (4 models) |
| Latency per frame | 6–8 ms | 24–32 ms |
| Max cameras at 2.5 FPS | 8 cameras | 2 cameras |
| Training complexity | One dataset, one pipeline | Four separate pipelines |
| Maintenance burden | Low | High (4× versioning) |

The RTX 3060 12 GB budget demands a single model when running 8 cameras.

---

## 2. Prerequisites

### Hardware (Pick One)

| Option | GPU | VRAM | Training Time (est.) |
|:---|:---|:---|:---|
| **Edge Node B** (on-site) | RTX 3060 | 12 GB | 8–12 hours |
| **Cloud GPU** (Lambda Labs, RunPod, Vast.ai) | A100 | 40/80 GB | 2–4 hours |
| **Google Colab Pro+** | A100 / L4 | 15–40 GB | 3–6 hours |

> **Note:** Edge Node B can be used when it is **not** running production inference. Stop the `edge-ai` container first: `docker compose -f edge/docker-compose.yml down`.

### Software

```bash
# 1. Python 3.10+ with CUDA-compatible PyTorch
python3 --version   # Must be 3.10 or higher

# 2. Install Ultralytics (includes YOLOv8 + all training utilities)
pip install ultralytics==8.3.*

# 3. Verify CUDA is available
python3 -c "import torch; print(f'CUDA available: {torch.cuda.is_available()}, Device: {torch.cuda.get_device_name(0)}')"
# Expected: CUDA available: True, Device: NVIDIA GeForce RTX 3060

# 4. Install annotation and data tools
pip install cvat-sdk roboflow fiftyone albumentations
```

### Disk Space

```
Training dataset:     ~5 GB   (25K images at ~200 KB avg)
Training artifacts:   ~3 GB   (checkpoints, logs, cache)
TensorRT engine:      ~15 MB  (exported model)
──────────────────────────────
Total:                ~8 GB minimum free space
```

---

## 3. Class Taxonomy

The model recognizes **10 classes** — 7 alert classes and 3 suppression classes.

```
ID  Class       Category            Alert Priority   Purpose
──  ─────       ────────            ──────────────   ───────
0   person      Human               high             Intruder detection + LoRa fusion
1   child       Human               critical         Pond zero-tolerance safety
2   cow         Livestock            warning          Containment breach detection
3   snake       Hazard/Reptile       high             All species combined
4   scorpion    Hazard/Arthropod     high             Ground-level detection
5   fire        Hazard/Thermal       critical         Visible flames
6   smoke       Hazard/Thermal       high             Early fire indicator
7   dog         Animal (suppress)    info             Reduce false person alerts
8   vehicle     Object (suppress)    info             Reduce false alerts at gate
9   bird        Animal (suppress)    info             Reduce false scorpion/snake alerts
```

**Why suppression classes (7–9)?**

Without them, the model confuses similar objects:
- A **dog** near the perimeter triggers a false "person detected"
- A **bird** in-frame triggers a false "scorpion detected"
- A **tractor** parked overnight triggers a false "intruder" alert

Training the model to explicitly recognize these prevents cross-class confusion.

---

## 4. Dataset Acquisition

The model requires approximately **15,000–25,000 annotated images**. Source from public datasets first, then supplement with on-site collection.

### 4.1 Public Dataset Sources

| Class | Public Dataset | Download | Target Count |
|:---|:---|:---|:---|
| person | COCO 2017 | [cocodataset.org](https://cocodataset.org) | 5,000 |
| child | VisDrone, COCO subset (small persons) | [VisDrone](https://github.com/VisDrone/VisDrone-Dataset) | 1,500 |
| cow | Open Images V7 — cattle subset | [storage.googleapis.com](https://storage.googleapis.com/openimages/web/index.html) | 2,000 |
| snake | Roboflow "Snake Detection", iNaturalist India | [roboflow.com](https://roboflow.com/), [inaturalist.org](https://www.inaturalist.org/) | 2,500 |
| scorpion | Roboflow "Scorpion Detection", iNaturalist | [roboflow.com](https://roboflow.com/) | 1,500 |
| fire | FireNet (Dunnings & Breckon), Roboflow "Fire Detection v2" | [roboflow.com](https://roboflow.com/) | 2,000 |
| smoke | FIRESENSE, HPWREN Wildfire Smoke Detection | [zenodo.org](https://zenodo.org/) | 1,500 |
| dog | COCO dog class, Open Images | [cocodataset.org](https://cocodataset.org) | 1,000 |
| vehicle | COCO car/truck, VisDrone | [cocodataset.org](https://cocodataset.org) | 1,000 |
| bird | COCO bird class, iNaturalist | [cocodataset.org](https://cocodataset.org) | 500 |

### 4.2 Downloading with Roboflow (Example)

```python
from roboflow import Roboflow

rf = Roboflow(api_key="YOUR_API_KEY")

# Download snake detection dataset in YOLO format
project = rf.workspace("your-workspace").project("snake-detection")
dataset = project.version(1).download("yolov8")

# Download scorpion detection dataset
project = rf.workspace("your-workspace").project("scorpion-detection")
dataset = project.version(1).download("yolov8")

# Download fire detection dataset
project = rf.workspace("your-workspace").project("fire-detection-v2")
dataset = project.version(1).download("yolov8")
```

### 4.3 Downloading from COCO (Example)

```python
import fiftyone as fo
import fiftyone.zoo as foz

# Download COCO images containing persons, dogs, birds, cows, vehicles
dataset = foz.load_zoo_dataset(
    "coco-2017",
    split="train",
    label_types=["detections"],
    classes=["person", "dog", "bird", "cow", "car", "truck"],
    max_samples=10000,
)

# Export in YOLO format
dataset.export(
    export_dir="/datasets/coco_subset",
    dataset_type=fo.types.YOLOv5Dataset,  # Compatible with YOLOv8
)
```

### 4.4 Class ID Remapping

Public datasets use their own class IDs. You **must** remap them to SudarshanChakra's taxonomy (0–9) before merging. Create a remapping script:

```python
"""remap_classes.py — Remap public dataset class IDs to SudarshanChakra taxonomy."""

import os
import glob

SC_CLASS_MAP = {
    "person": 0,
    "child": 1,
    "cow": 2,
    "snake": 3,
    "scorpion": 4,
    "fire": 5,
    "smoke": 6,
    "dog": 7,
    "vehicle": 8, "car": 8, "truck": 8, "tractor": 8, "motorcycle": 8,
    "bird": 9,
}

def remap_label_file(label_path: str, source_names: dict):
    """
    Remap class IDs in a YOLO label file.
    
    Args:
        label_path: Path to the .txt label file.
        source_names: Dict mapping source class ID → class name string.
    """
    lines = []
    with open(label_path) as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) < 5:
                continue
            
            src_id = int(parts[0])
            src_name = source_names.get(src_id, "").lower()
            
            if src_name in SC_CLASS_MAP:
                parts[0] = str(SC_CLASS_MAP[src_name])
                lines.append(" ".join(parts))
            # Skip classes not in our taxonomy
    
    with open(label_path, "w") as f:
        f.write("\n".join(lines) + "\n" if lines else "")


def remap_dataset(labels_dir: str, source_names: dict):
    """Remap all label files in a directory."""
    for path in glob.glob(os.path.join(labels_dir, "*.txt")):
        remap_label_file(path, source_names)
    print(f"Remapped {len(glob.glob(os.path.join(labels_dir, '*.txt')))} label files")


# Example for COCO:
COCO_NAMES = {0: "person", 15: "bird", 16: "cat", 17: "dog", 19: "cow", 2: "car", 7: "truck"}
# remap_dataset("/datasets/coco_subset/labels/train", COCO_NAMES)
```

---

## 5. On-Site Data Collection

Public datasets provide volume but **not accuracy** for your specific farm. On-site images match the exact camera angles, soil color, lighting, and vegetation that the model will encounter in production.

### 5.1 Phase 1 — Passive Recording (7 Days)

Install all 8 cameras and record without AI to capture diverse conditions.

```bash
# Record RTSP streams at 1 FPS to disk (saves ~2 GB/camera/day)
# Run on Edge Node during the 7-day collection period

ffmpeg -rtsp_transport tcp \
    -i "rtsp://admin:farm2024@192.168.1.201:554/stream2" \
    -vf fps=1 \
    -q:v 2 \
    -strftime 1 \
    "/data/collection/cam-01/frame_%Y%m%d_%H%M%S.jpg"
```

**Capture across all lighting conditions:**

```
Time              Condition              Why It Matters
05:00–06:00       Dawn (low light)       Warm color cast, long shadows
08:00–10:00       Morning (bright)       Clean lighting, good contrast
12:00–14:00       Midday (harsh)         Strong shadows, washed colors
16:00–18:00       Evening (golden)       Orange tint, oblique shadows
18:30–05:00       Night (IR mode)        Grayscale, IR illumination
                  Rain                    Reflections, reduced visibility
                  Fog/mist               Low contrast, haze
```

### 5.2 Phase 2 — Frame Curation

Extract diverse representative frames from the raw recordings.

```bash
# Extract every 30th frame from a camera's day recording → ~200 frames/camera/day
# Total: 200 frames × 8 cameras × 7 days = 11,200 candidate frames

# Manually select frames with:
#   ✓ Workers walking through zones
#   ✓ Cattle in/out of pen
#   ✓ Various lighting (dawn, noon, dusk, night)
#   ✓ Empty frames (critical for negative examples — teach model "nothing here")
#   ✓ Weather variation (rain, mist, clear, overcast)
```

### 5.3 Phase 3 — Synthetic Rare-Class Staging

For classes that are rare or dangerous, stage controlled scenarios:

| Class | Staging Method | Safety Notes |
|:---|:---|:---|
| snake | Place rubber snake replicas at various positions in camera FOV | Use realistic replicas in multiple poses (coiled, stretched) |
| scorpion | Print high-quality scorpion images, place at ground level | Multiple sizes, angles; include on different soil types |
| fire | Controlled small fire in a metal barrel, supervised | **Always have fire extinguisher ready.** Record for 5 min, then extinguish. |
| smoke | Smoke generators or controlled smoldering | Outdoor only. Capture wisps and dense plumes separately. |
| child | Photograph children of known workers (with consent) walking near pond | Only during safe, supervised conditions |

These synthetic placements are critical because they capture objects at the **exact camera angles and backgrounds** that the model will encounter.

---

## 6. Annotation with CVAT

### 6.1 Deploy CVAT

Self-host CVAT on Edge Node B (or any machine with a browser):

```bash
# Deploy CVAT with Docker Compose
git clone https://github.com/cvat-ai/cvat.git
cd cvat
docker compose up -d

# Access at: http://localhost:8080
# Default admin: create on first launch
```

### 6.2 Create a Project

1. Navigate to **Projects → Create Project**
2. Name: `SudarshanChakra v1`
3. Add labels (use exact names — must match `data.yaml`):

```
person, child, cow, snake, scorpion, fire, smoke, dog, vehicle, bird
```

4. Set label colors for visual clarity:
   - person: `#00FF00` (green)
   - child: `#FF00FF` (magenta)
   - cow: `#FFFF00` (yellow)
   - snake: `#FF0000` (red)
   - scorpion: `#FF8800` (orange)
   - fire: `#FF4444` (bright red)
   - smoke: `#888888` (gray)
   - dog: `#00FFFF` (cyan)
   - vehicle: `#0088FF` (blue)
   - bird: `#AAAAFF` (light blue)

### 6.3 Annotation Rules

**Bounding Box Rules:**

```
  ┌──────────────────────┐
  │  Tightly enclose the │
  │  VISIBLE portion of  │
  │  the object. No      │
  │  excessive padding.  │
  └──────────────────────┘

  Partially occluded (>30% visible) → annotate visible portion
  Partially occluded (<30% visible) → skip
  Multiple objects → one box per object (do not group)
```

**Class-Specific Rules:**

| Class | Annotation Notes |
|:---|:---|
| person | Head to feet, including extended limbs. Any adult or teenager (age > ~12). |
| child | Head to feet. Any human appearing age < ~12. When unsure, label as `person`. |
| cow | Full body including head and tail. Bulls and calves also labeled `cow`. |
| snake | Entire visible body including curves. Often S-shaped — use a tight bounding box around the full extent. |
| scorpion | Entire body including pincers and tail. Very small — zoom in to annotate precisely. |
| fire | Visible flames only. Exclude glow, reflections, embers, and sparks. |
| smoke | Dense smoke plume. Exclude transparent wisps, steam, and dust clouds. |
| dog | Full body. Includes all canines (farm dogs, strays). |
| vehicle | Full vehicle body. Includes cars, trucks, tractors, motorcycles. |
| bird | Full body. Often in flight — tight box around wing-to-wing extent. |

**Quality Assurance:**

```
  Standard: Dual-annotator review
  ──────────────────────────────────
  1. Every image annotated by 2 people independently
  2. Disagreements resolved by a third reviewer
  3. IoU threshold for agreement: 0.7
  4. Target: >95% inter-annotator agreement
  5. Spot-check 5% of final dataset for errors before training
```

### 6.4 Export from CVAT

```
  Tasks → Export task dataset → Format: YOLO 1.1 → Download ZIP
  
  This produces:
  └── labels/
      ├── frame_001.txt    # "3 0.45 0.62 0.12 0.08"  (class x_center y_center w h)
      ├── frame_002.txt
      └── ...
```

---

## 7. Dataset Preparation

### 7.1 Directory Structure

Organize all images and labels into the YOLO directory format:

```
/datasets/sudarshanchakra_v1/
├── data.yaml                  # Dataset configuration
├── train/
│   ├── images/                # 80% of all images
│   │   ├── coco_person_0001.jpg
│   │   ├── roboflow_snake_0042.jpg
│   │   ├── onsite_cam03_20240315_1423.jpg
│   │   └── ...
│   └── labels/                # Matching label files (same filenames, .txt extension)
│       ├── coco_person_0001.txt
│       ├── roboflow_snake_0042.txt
│       ├── onsite_cam03_20240315_1423.txt
│       └── ...
├── val/
│   ├── images/                # 15% of all images
│   └── labels/
└── test/
    ├── images/                # 5% held-out for final evaluation
    └── labels/
```

### 7.2 data.yaml

Create the dataset config file that YOLO reads during training:

```yaml
# /datasets/sudarshanchakra_v1/data.yaml
# SudarshanChakra YOLOv8 Custom Model Dataset

path: /datasets/sudarshanchakra_v1
train: train/images
val: val/images
test: test/images

nc: 10

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

### 7.3 Label File Format

Each `.txt` label file contains one line per object in the image. The format is:

```
<class_id> <x_center> <y_center> <width> <height>
```

All coordinates are **normalized** (0.0 to 1.0) relative to image dimensions.

**Example:** An image (640×480) with a person at pixel bbox `[100, 50, 300, 430]`:

```
# Calculation:
#   x_center = (100 + 300) / 2 / 640 = 0.3125
#   y_center = (50 + 430)  / 2 / 480 = 0.5000
#   width    = (300 - 100)     / 640 = 0.3125
#   height   = (430 - 50)      / 480 = 0.7917

0 0.3125 0.5000 0.3125 0.7917
```

Multiple objects in one image → multiple lines:

```
0 0.3125 0.5000 0.3125 0.7917
3 0.7200 0.8500 0.0800 0.0400
7 0.1500 0.6000 0.1200 0.2000
```

(person at center, snake bottom-right, dog left-side)

### 7.4 Dataset Splitting Script

```python
"""split_dataset.py — Split combined images+labels into train/val/test."""

import os
import random
import shutil
from pathlib import Path

DATASET_DIR = "/datasets/sudarshanchakra_v1"
ALL_IMAGES_DIR = "/datasets/sudarshanchakra_v1/all_images"
ALL_LABELS_DIR = "/datasets/sudarshanchakra_v1/all_labels"

TRAIN_RATIO = 0.80
VAL_RATIO = 0.15
TEST_RATIO = 0.05

random.seed(42)

# Gather all image files
image_files = sorted([
    f for f in os.listdir(ALL_IMAGES_DIR)
    if f.lower().endswith((".jpg", ".jpeg", ".png"))
])

random.shuffle(image_files)

n = len(image_files)
train_end = int(n * TRAIN_RATIO)
val_end = train_end + int(n * VAL_RATIO)

splits = {
    "train": image_files[:train_end],
    "val": image_files[train_end:val_end],
    "test": image_files[val_end:],
}

for split_name, files in splits.items():
    img_dir = Path(DATASET_DIR) / split_name / "images"
    lbl_dir = Path(DATASET_DIR) / split_name / "labels"
    img_dir.mkdir(parents=True, exist_ok=True)
    lbl_dir.mkdir(parents=True, exist_ok=True)

    for fname in files:
        # Copy image
        shutil.copy2(os.path.join(ALL_IMAGES_DIR, fname), img_dir / fname)

        # Copy matching label (if exists; images without labels = negative examples)
        label_name = Path(fname).stem + ".txt"
        label_src = os.path.join(ALL_LABELS_DIR, label_name)
        if os.path.exists(label_src):
            shutil.copy2(label_src, lbl_dir / label_name)

    print(f"{split_name}: {len(files)} images")

print(f"\nTotal: {n} images split into train/val/test at {TRAIN_RATIO}/{VAL_RATIO}/{TEST_RATIO}")
```

### 7.5 Dataset Validation

Before training, verify your dataset is well-formed:

```python
"""validate_dataset.py — Check dataset integrity before training."""

import os
from pathlib import Path
from collections import Counter

DATASET_DIR = "/datasets/sudarshanchakra_v1"
CLASS_NAMES = ["person", "child", "cow", "snake", "scorpion",
               "fire", "smoke", "dog", "vehicle", "bird"]
NC = len(CLASS_NAMES)

for split in ["train", "val", "test"]:
    img_dir = Path(DATASET_DIR) / split / "images"
    lbl_dir = Path(DATASET_DIR) / split / "labels"

    images = set(p.stem for p in img_dir.glob("*") if p.suffix.lower() in {".jpg", ".jpeg", ".png"})
    labels = set(p.stem for p in lbl_dir.glob("*.txt"))

    missing_labels = images - labels
    orphan_labels = labels - images

    class_counts = Counter()
    bad_lines = 0

    for lbl_file in lbl_dir.glob("*.txt"):
        with open(lbl_file) as f:
            for line_num, line in enumerate(f, 1):
                parts = line.strip().split()
                if len(parts) != 5:
                    bad_lines += 1
                    continue
                cls_id = int(parts[0])
                if cls_id < 0 or cls_id >= NC:
                    print(f"  WARNING: {lbl_file.name}:{line_num} — invalid class ID {cls_id}")
                    bad_lines += 1
                    continue
                class_counts[cls_id] += 1

    print(f"\n{'=' * 50}")
    print(f"Split: {split}")
    print(f"  Images:          {len(images)}")
    print(f"  Labels:          {len(labels)}")
    print(f"  Missing labels:  {len(missing_labels)} (images without annotations = negative examples)")
    print(f"  Orphan labels:   {len(orphan_labels)} (labels without images — DELETE these)")
    print(f"  Bad lines:       {bad_lines}")
    print(f"  Class distribution:")
    total = sum(class_counts.values())
    for cls_id in range(NC):
        count = class_counts.get(cls_id, 0)
        pct = count / total * 100 if total else 0
        bar = "█" * int(pct)
        flag = " ⚠️ LOW" if pct < 3 else ""
        print(f"    {cls_id:2d} {CLASS_NAMES[cls_id]:12s}  {count:6d}  ({pct:5.1f}%)  {bar}{flag}")

    if orphan_labels:
        print(f"\n  ⚠️  Delete orphan labels: {', '.join(list(orphan_labels)[:5])}...")
```

### 7.6 Class Imbalance

If any class represents < 5% of total annotations, balance the dataset:

**Option A — Oversample rare classes:**

```bash
# Duplicate rare-class images 2–3× in the training set
# For example, if scorpion has 500 images but person has 5000:
# Copy each scorpion image 3 times with unique filenames
for f in /datasets/sudarshanchakra_v1/train/images/scorpion_*.jpg; do
    cp "$f" "${f%.jpg}_dup1.jpg"
    cp "$f" "${f%.jpg}_dup2.jpg"
    # Copy labels too
    base=$(basename "$f" .jpg)
    cp "/datasets/sudarshanchakra_v1/train/labels/${base}.txt" \
       "/datasets/sudarshanchakra_v1/train/labels/${base}_dup1.txt"
    cp "/datasets/sudarshanchakra_v1/train/labels/${base}.txt" \
       "/datasets/sudarshanchakra_v1/train/labels/${base}_dup2.txt"
done
```

**Option B — Rely on built-in augmentation (recommended):**

YOLOv8 handles imbalance via focal loss and augmentation:
- `copy_paste=0.3` — pastes small object instances into other images (great for snake/scorpion)
- `mosaic=1.0` — combines 4 images into 1, increasing rare-class frequency
- These are enabled by default in the training command below

---

## 8. Training

### 8.1 Full Training Command

```bash
# Navigate to the edge directory (has ultralytics installed)
cd /path/to/SudarshanChakra/edge

# Full training command with all hyperparameters
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
    hsv_h=0.015 \
    hsv_s=0.7 \
    hsv_v=0.4 \
    degrees=5.0 \
    translate=0.1 \
    scale=0.5 \
    shear=2.0 \
    perspective=0.0005 \
    flipud=0.5 \
    fliplr=0.5 \
    mosaic=1.0 \
    mixup=0.15 \
    copy_paste=0.3
```

### 8.2 Parameter Explanation

**Core Parameters:**

| Parameter | Value | Meaning |
|:---|:---|:---|
| `model` | `yolov8n.pt` | Start from Ultralytics pre-trained nano weights (transfer learning) |
| `data` | `data.yaml` | Path to dataset config |
| `epochs` | `300` | Maximum training iterations over the full dataset |
| `imgsz` | `640` | All images resized to 640×640 pixels |
| `batch` | `16` | Process 16 images at a time (reduce to 8 if GPU runs out of memory) |
| `patience` | `50` | Stop early if no mAP improvement for 50 consecutive epochs |
| `device` | `0` | Use GPU 0 |
| `workers` | `8` | Data loading threads (set to CPU core count) |

**Learning Rate:**

| Parameter | Value | Meaning |
|:---|:---|:---|
| `lr0` | `0.01` | Initial learning rate |
| `lrf` | `0.01` | Final LR = lr0 × lrf = 0.0001 (cosine annealing) |
| `warmup_epochs` | `3.0` | Gradually ramp LR during first 3 epochs |

**Loss Weights:**

| Parameter | Value | Meaning |
|:---|:---|:---|
| `box` | `7.5` | Bounding box regression loss weight |
| `cls` | `0.5` | Classification loss weight |
| `dfl` | `1.5` | Distribution focal loss weight |

**Augmentation:**

| Parameter | Value | Effect |
|:---|:---|:---|
| `hsv_h` | `0.015` | Random hue shift ±1.5% (lighting variation) |
| `hsv_s` | `0.7` | Random saturation shift ±70% (dawn/dusk simulation) |
| `hsv_v` | `0.4` | Random brightness shift ±40% (shadow/highlight) |
| `mosaic` | `1.0` | Combine 4 images into 1 tile — 100% of batches |
| `mixup` | `0.15` | Blend 2 images together — 15% of batches |
| `copy_paste` | `0.3` | Paste objects from one image onto another — 30% |
| `flipud` | `0.5` | Vertical flip — 50% (snakes can be any orientation) |
| `fliplr` | `0.5` | Horizontal flip — 50% |

### 8.3 Transfer Learning Phases

```
Phase 1: Initial fine-tune (this training command)
  ├── Starts from yolov8n.pt (COCO pre-trained)
  ├── Already knows: person, car, truck, dog, bird, cow
  ├── Learns: snake, scorpion, fire, smoke, child
  ├── Unfreezes ALL layers (full fine-tune, not just head)
  ├── ~300 epochs with early stopping
  └── Expected: 8–12 hours on RTX 3060

Phase 2: Farm-specific refinement (after 2+ weeks of live operation)
  ├── Starts from Phase 1 best weights
  ├── Uses: Phase 1 dataset + 2 weeks of on-site corrections
  ├── Lower learning rate: lr0=0.001
  ├── 100 epochs
  └── Corrects farm-specific false positives

  Command:
  yolo detect train \
      model=runs/train/sc_v1/weights/best.pt \
      data=/datasets/sudarshanchakra_v2/data.yaml \
      epochs=100 \
      lr0=0.001 \
      patience=30 \
      name=sc_v2
```

### 8.4 Monitoring Training

Training produces real-time logs and a TensorBoard dashboard:

```bash
# Watch training progress in terminal
tail -f runs/train/sc_v1/results.csv

# Launch TensorBoard for visual metrics
tensorboard --logdir runs/train/sc_v1 --port 6006
# Open http://localhost:6006 in browser
```

**Key metrics to watch:**

| Metric | Good Sign | Bad Sign |
|:---|:---|:---|
| `train/box_loss` | Steadily decreasing | Plateaus very early |
| `train/cls_loss` | Steadily decreasing | Oscillating wildly |
| `val/mAP50` | Increasing toward > 0.78 | Stuck below 0.60 |
| `val/mAP50-95` | Increasing toward > 0.55 | Flat or decreasing |

**Training outputs:**

```
runs/train/sc_v1/
├── weights/
│   ├── best.pt        ← Best model (highest val mAP) — USE THIS
│   └── last.pt        ← Last epoch checkpoint
├── results.csv        ← Per-epoch metrics table
├── results.png        ← Training curves (loss, mAP, precision, recall)
├── confusion_matrix.png
├── F1_curve.png
├── PR_curve.png
├── P_curve.png
├── R_curve.png
├── val_batch0_pred.jpg    ← Visual predictions on validation images
└── args.yaml              ← Saved training config (reproducibility)
```

### 8.5 Google Colab (Alternative)

If you don't have local GPU access, use this Colab cell:

```python
# Cell 1: Install and verify
!pip install ultralytics
import torch
print(f"GPU: {torch.cuda.get_device_name(0)}, CUDA: {torch.version.cuda}")

# Cell 2: Mount Google Drive (for dataset)
from google.colab import drive
drive.mount('/content/drive')

# Cell 3: Train
!yolo detect train \
    model=yolov8n.pt \
    data=/content/drive/MyDrive/datasets/sudarshanchakra_v1/data.yaml \
    epochs=300 imgsz=640 batch=16 patience=50 device=0 \
    project=/content/drive/MyDrive/sc_training \
    name=sc_v1

# Cell 4: Download best weights
from google.colab import files
files.download("/content/drive/MyDrive/sc_training/sc_v1/weights/best.pt")
```

---

## 9. Validation & Acceptance Criteria

### 9.1 Run Validation

```bash
# Validate against the held-out test set
yolo detect val \
    model=runs/train/sc_v1/weights/best.pt \
    data=/datasets/sudarshanchakra_v1/data.yaml \
    split=test \
    imgsz=640 \
    device=0
```

### 9.2 Minimum Accuracy Thresholds

The model **must** meet these per-class mAP@0.5 thresholds before deployment:

```
Class       Minimum mAP@0.5    Rationale
─────       ───────────────    ─────────
person      > 0.85             High — primary detection class, most data available
child       > 0.70             Moderate — harder (size-based differentiation)
cow         > 0.88             High — distinct shape, abundant data
snake       > 0.72             Moderate — challenging (camouflage, thin shape)
scorpion    > 0.65             Lower — very small objects, limited data
fire        > 0.82             High — safety-critical, color confirmation helps
smoke       > 0.75             Moderate — confused with clouds/dust
dog         > 0.85             High — suppress false person alerts
vehicle     > 0.90             High — distinct shape, abundant data
bird        > 0.80             High — suppress false scorpion/snake alerts

Overall mAP@0.5:       > 0.78
Overall mAP@0.5:0.95:  > 0.55
```

### 9.3 Inference Speed Verification

```bash
# Benchmark inference speed (must meet real-time requirements)
yolo detect predict \
    model=runs/train/sc_v1/weights/best.pt \
    source=/datasets/sudarshanchakra_v1/test/images \
    imgsz=640 \
    device=0
```

**Required speeds on RTX 3060:**

| Format | Target Latency | Target Throughput |
|:---|:---|:---|
| PyTorch FP32 (.pt) | < 15 ms/frame | > 67 FPS |
| TensorRT FP16 (.engine) | < 8 ms/frame | > 125 FPS |

The production system runs 8 cameras × 2.5 FPS = 20 inferences/sec. At 6 ms/inference (TensorRT FP16), GPU utilization is only ~12%.

### 9.4 What to Do If Metrics Are Below Threshold

| Problem | Fix |
|:---|:---|
| Class mAP too low | Add more training images for that class (especially on-site images) |
| Overall mAP too low | Train for more epochs; increase dataset size; try YOLOv8s (larger model) |
| High false positives for a class | Add hard-negative examples (things confused with that class) |
| snake confused with rope | Add rope/hose images labeled as background (no annotation) |
| scorpion mAP very low | Enable SAHI for scorpion cameras (see `pipeline.py`); collect more data |
| Overfitting (train mAP ≫ val mAP) | Increase augmentation; add more diverse training data |

---

## 10. TensorRT Export

After training passes validation, export to TensorRT for 2.5× faster inference.

### 10.1 Export Command

```bash
# Export PyTorch model to TensorRT FP16 engine
yolo export \
    model=runs/train/sc_v1/weights/best.pt \
    format=engine \
    half=True \
    device=0 \
    workspace=4 \
    simplify=True

# This produces: runs/train/sc_v1/weights/best.engine (~15 MB)
# Export takes 2–3 minutes on RTX 3060
```

> **IMPORTANT:** The TensorRT engine is **GPU-specific**. An engine built on RTX 3060 will NOT run on a Jetson Orin. You must export on the **same GPU architecture** as the deployment target. If deploying to both Node A and Node B with different GPUs, export separately on each.

### 10.2 Verify the Engine

```python
from ultralytics import YOLO

# Load the exported engine
model = YOLO("runs/train/sc_v1/weights/best.engine")

# Run a quick prediction
results = model.predict(
    source="/datasets/sudarshanchakra_v1/test/images",
    imgsz=640,
    conf=0.40,
    device=0,
)

# Check latency
print(f"Average inference time: {results[0].speed['inference']:.1f} ms")
# Expected: ~6 ms on RTX 3060
```

### 10.3 Performance Comparison

```
Format            Latency    Throughput    VRAM      mAP Impact
────────          ───────    ──────────    ────      ──────────
PyTorch FP32      ~15 ms     67 FPS       ~400 MB   Baseline
ONNX FP32         ~12 ms     83 FPS       ~350 MB   None
TensorRT FP32     ~8 ms      125 FPS      ~250 MB   None
TensorRT FP16     ~6 ms      167 FPS      ~200 MB   < 0.5% drop  ← USE THIS
TensorRT INT8     ~4 ms      250 FPS      ~150 MB   ~2–3% drop   ← NOT recommended
```

FP16 is the sweet spot — nearly identical accuracy with 2.5× speedup over PyTorch.

---

## 11. Deployment to Edge Nodes

### 11.1 Copy Model Files

```bash
# From the training machine, copy to Edge Nodes via VPN

# Copy to Edge Node A (10.8.0.10)
scp runs/train/sc_v1/weights/best.pt   admin@10.8.0.10:/app/models/yolov8n_farm.pt
scp runs/train/sc_v1/weights/best.engine admin@10.8.0.10:/app/models/yolov8n_farm.engine

# Copy to Edge Node B (10.8.0.11)
scp runs/train/sc_v1/weights/best.pt   admin@10.8.0.11:/app/models/yolov8n_farm.pt
scp runs/train/sc_v1/weights/best.engine admin@10.8.0.11:/app/models/yolov8n_farm.engine
```

### 11.2 Restart the Edge Container

```bash
# On each edge node
cd /path/to/SudarshanChakra/edge
docker compose restart edge-ai

# Verify the new model is loaded
docker logs -f edge-ai 2>&1 | head -20
# Should show: "Loading cached TensorRT engine: /app/models/yolov8n_farm.engine"
```

### 11.3 How `pipeline.py` Loads the Model

The `load_model()` function in `edge/pipeline.py` handles model loading automatically:

```
Priority order:
  1. /app/models/yolov8n_farm.engine   ← TensorRT engine (fastest)
  2. /app/models/yolov8n_farm.pt       ← PyTorch weights (auto-exports to TensorRT on first run)
  3. yolov8n.pt (downloaded)           ← Ultralytics base model (fallback, no custom classes)
```

On the **first container start** with only a `.pt` file, the system automatically exports to TensorRT. This takes 2–3 minutes and the engine is cached on the mounted volume for subsequent restarts.

### 11.4 Model Versioning

Keep previous model versions in case rollback is needed:

```
/app/models/
├── yolov8n_farm.engine          ← Active production model (symlink)
├── yolov8n_farm.pt              ← Active production weights (symlink)
├── yolov8n_farm_v1.0.engine     ← Version 1.0
├── yolov8n_farm_v1.0.pt
├── yolov8n_farm_v1.1.engine     ← Version 1.1
├── yolov8n_farm_v1.1.pt
└── model_config.json            ← Active model pointer
```

```json
{
  "active_model": "yolov8n_farm_v1.1.engine",
  "previous_model": "yolov8n_farm_v1.0.engine",
  "trained_on": "2024-03-15",
  "dataset_version": "v1.1",
  "mAP50": 0.82,
  "per_class_mAP50": {
    "person": 0.91, "child": 0.73, "cow": 0.90, "snake": 0.76,
    "scorpion": 0.68, "fire": 0.85, "smoke": 0.78,
    "dog": 0.88, "vehicle": 0.93, "bird": 0.84
  }
}
```

---

## 12. Retraining & Continuous Improvement

### 12.1 When to Retrain

| Trigger | Action |
|:---|:---|
| False positive rate > 10% | Collect hard-negative examples, retrain |
| New threat type identified | Add new class, expand dataset, retrain from scratch |
| Camera added or repositioned | Collect 1 week of on-site data from new camera, retrain |
| Seasonal lighting changes | After monsoon season, retrain with wet/muddy images |
| Model accuracy degrades | Investigate drift; collect fresh data; Phase 2 fine-tune |

### 12.2 Collecting Hard Negatives

When the model produces false positives in production, save those frames and include them in retraining — **without annotations** (as negative examples) or with correct annotations.

```bash
# On the edge node, save false positive frames to a collection directory
# The dashboard "Mark as False Positive" action should trigger this

# Example: frame where rope was detected as snake
# Save frame → annotate correctly in CVAT (no snake label) → add to training set
```

### 12.3 Retraining Command (Phase 2)

```bash
yolo detect train \
    model=runs/train/sc_v1/weights/best.pt \
    data=/datasets/sudarshanchakra_v2/data.yaml \
    epochs=100 \
    imgsz=640 \
    batch=16 \
    patience=30 \
    lr0=0.001 \
    device=0 \
    project=runs/train \
    name=sc_v2
```

### 12.4 OTA Model Update (Remote)

Push a new model to edge nodes without physical access:

```bash
# 1. Copy new model to Edge Node A via VPN
rsync -av --progress \
    runs/train/sc_v2/weights/best.engine \
    admin@10.8.0.10:/app/models/yolov8n_farm_v2.0.engine

rsync -av --progress \
    runs/train/sc_v2/weights/best.pt \
    admin@10.8.0.10:/app/models/yolov8n_farm_v2.0.pt

# 2. Update symlinks on edge node
ssh admin@10.8.0.10 "cd /app/models && \
    ln -sf yolov8n_farm_v2.0.engine yolov8n_farm.engine && \
    ln -sf yolov8n_farm_v2.0.pt yolov8n_farm.pt"

# 3. Restart edge container to load new model
ssh admin@10.8.0.10 "cd /path/to/edge && docker compose restart edge-ai"

# 4. Verify via MQTT heartbeat (check model_version field)
```

---

## 13. Troubleshooting

### Training Issues

| Symptom | Cause | Fix |
|:---|:---|:---|
| `CUDA out of memory` | Batch size too large | Reduce `batch` from 16 → 8 → 4 |
| `No labels found` | Labels directory missing or empty | Verify `data.yaml` paths; check label files exist |
| `Image not found` | Image files not at expected paths | Run `validate_dataset.py`; check data.yaml `path` |
| Training loss = NaN | Learning rate too high; corrupt images | Reduce `lr0` to 0.001; remove corrupt images |
| mAP not improving | Dataset too small or too similar | Add more diverse images; increase augmentation |
| mAP drops after epoch 50 | Overfitting | Enable/increase dropout; add more training data |

### Export Issues

| Symptom | Cause | Fix |
|:---|:---|:---|
| TensorRT export fails | Wrong CUDA/TensorRT version | Match CUDA version to GPU driver; reinstall tensorrt |
| Engine runs on wrong GPU | GPU architecture mismatch | Re-export on target GPU |
| Engine much slower than expected | FP16 not supported | Check `nvidia-smi`; use `half=False` for FP32 |

### Deployment Issues

| Symptom | Cause | Fix |
|:---|:---|:---|
| `Custom model not found` | Wrong path or filename | Check `ENGINE_PATH` / `PT_PATH` env vars in docker-compose |
| Model detects COCO classes | Using base `yolov8n.pt` instead of custom | Verify custom `.pt`/`.engine` file exists at expected path |
| Low confidence on farm images | Model not trained on on-site data | Collect on-site images and retrain (Phase 2) |

---

## 14. Quick Reference

### Minimal Training (Copy-Paste Commands)

```bash
# 1. Install
pip install ultralytics

# 2. Prepare data.yaml (see Section 7.2)

# 3. Train
yolo detect train model=yolov8n.pt data=/datasets/sudarshanchakra_v1/data.yaml \
    epochs=300 imgsz=640 batch=16 patience=50 device=0

# 4. Validate
yolo detect val model=runs/train/sc_v1/weights/best.pt \
    data=/datasets/sudarshanchakra_v1/data.yaml split=test

# 5. Export
yolo export model=runs/train/sc_v1/weights/best.pt format=engine half=True

# 6. Deploy
scp runs/train/sc_v1/weights/best.engine admin@10.8.0.10:/app/models/yolov8n_farm.engine
scp runs/train/sc_v1/weights/best.pt admin@10.8.0.10:/app/models/yolov8n_farm.pt
ssh admin@10.8.0.10 "cd /path/to/edge && docker compose restart edge-ai"
```

### Key File Paths

| File | Purpose |
|:---|:---|
| `edge/pipeline.py` | Model loading and inference pipeline |
| `edge/config/cameras.json` | Camera RTSP URLs and FPS settings |
| `edge/config/zones.json` | Virtual fence polygon definitions |
| `docs/AI_DETECTION_ARCHITECTURE.md` | Deep technical reference for all detection subsystems |
| `AGENT_INSTRUCTIONS.md` (Phase 8) | High-level training overview |

### Environment Variables (Edge Container)

| Variable | Default | Purpose |
|:---|:---|:---|
| `ENGINE_PATH` | `/app/models/yolov8n_farm.engine` | TensorRT engine file |
| `PT_PATH` | `/app/models/yolov8n_farm.pt` | PyTorch weights file |
| `CONFIDENCE_THRESHOLD` | `0.40` | Global minimum detection confidence |
| `INPUT_SIZE` | `640` | Inference input resolution |
