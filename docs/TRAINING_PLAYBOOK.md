# TRAINING PLAYBOOK — Step-by-Step Model Training for Beginners

## Who This Is For

You are new to AI/ML training. You have a PC with an NVIDIA GPU (RTX 3060 or better). You want to train a model that detects snakes, scorpions, fire, smoke, people, children, and cows on your farm cameras. This guide walks you through every single step with exact commands you can copy-paste.

**Time needed:** 2-3 days total (mostly waiting for downloads and training)

**End result:** A file called `yolov8n_farm.pt` that your edge system uses to detect threats on your farm.

---

## What You'll Do (Big Picture)

```
Step 1: Set up your PC                   (30 minutes)
Step 2: Download training images          (1-2 hours)
Step 3: Record your own farm images       (7 days passive, then 2 hours to select)
Step 4: Label all images with boxes       (4-8 hours of clicking)
Step 5: Organize files into folders       (30 minutes)
Step 6: Train the model                   (8-12 hours — PC does the work while you sleep)
Step 7: Check if the model is good        (30 minutes)
Step 8: Convert for fast inference        (10 minutes)
Step 9: Deploy to your edge nodes        (15 minutes)
```

---

## Step 1: Set Up Your PC

### 1.1 Check Your GPU

Open a terminal and type:

```bash
nvidia-smi
```

You should see something like:

```
+-----------------------------------------------------------------------------+
| NVIDIA-SMI 535.129.03   Driver Version: 535.129.03   CUDA Version: 12.2     |
|-------------------------------+----------------------+----------------------+
| GPU  Name        Persistence-M| Bus-Id        Disp.A | Volatile Uncorr. ECC |
|   0  NVIDIA GeForce RTX 3060  |   00000000:01:00.0  Off |                  N/A |
+-------------------------------+----------------------+----------------------+
```

**If you see an error:** You need to install NVIDIA drivers first.
```bash
# Ubuntu 22.04/24.04:
sudo apt update
sudo apt install -y nvidia-driver-535
sudo reboot
# After reboot, run nvidia-smi again
```

### 1.2 Install Python and Training Tools

```bash
# Install Python 3.11 (if not already installed)
sudo apt install -y python3.11 python3.11-venv python3-pip

# Create a workspace folder
mkdir -p ~/farm-training
cd ~/farm-training

# Create a virtual environment (keeps things clean)
python3.11 -m venv venv
source venv/bin/activate

# Install YOLO training tools
pip install ultralytics==8.2.50

# Verify it works
yolo checks
```

You should see:
```
Ultralytics YOLOv8.2.50 🚀
Python-3.11.x torch-2.x.x CUDA:0 (NVIDIA GeForce RTX 3060, 12288MiB)
Setup complete ✅
```

**If you see "CUDA not available":** Install PyTorch with CUDA:
```bash
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
```

### 1.3 Install Other Tools You'll Need

```bash
# For downloading datasets
pip install roboflow

# For image processing
pip install opencv-python Pillow

# For annotation (we'll set up CVAT via Docker)
sudo apt install -y docker.io docker-compose
sudo usermod -aG docker $USER
# LOG OUT AND LOG BACK IN after this command
```

---

## Step 2: Download Training Images

Your model needs to see thousands of example images with labels showing where each object is. We'll download pre-labeled datasets from the internet.

### 2.1 Download Snake Images from Roboflow

```bash
cd ~/farm-training
mkdir -p datasets/downloaded
cd datasets/downloaded

# Install Roboflow CLI
pip install roboflow
```

Go to https://roboflow.com and create a FREE account. Then:

```python
# Save this as download_snakes.py
from roboflow import Roboflow

# Get your API key from: https://app.roboflow.com/settings/api
rf = Roboflow(api_key="YOUR_ROBOFLOW_API_KEY")  # ← Replace with your key

# Download snake detection dataset
project = rf.workspace().project("snake-detection-bbyrf")
version = project.version(1)
dataset = version.download("yolov8", location="./snakes")

print("Snake dataset downloaded!")
print(f"Images: {dataset.location}")
```

Run it:
```bash
python download_snakes.py
```

This gives you ~2,000 snake images with YOLO labels already drawn.

### 2.2 Download Scorpion Images

```python
# Save as download_scorpions.py
from roboflow import Roboflow

rf = Roboflow(api_key="YOUR_ROBOFLOW_API_KEY")
project = rf.workspace().project("scorpion-detection")
version = project.version(1)
dataset = version.download("yolov8", location="./scorpions")

print("Scorpion dataset downloaded!")
```

```bash
python download_scorpions.py
```

### 2.3 Download Fire and Smoke Images

```python
# Save as download_fire.py
from roboflow import Roboflow

rf = Roboflow(api_key="YOUR_ROBOFLOW_API_KEY")

# Fire dataset
project = rf.workspace().project("fire-detection-pmmsd")
version = project.version(1)
dataset = version.download("yolov8", location="./fire")

# Smoke dataset
project = rf.workspace().project("smoke-detection-bfnrc")
version = project.version(1)
dataset = version.download("yolov8", location="./smoke")

print("Fire and smoke datasets downloaded!")
```

```bash
python download_fire.py
```

### 2.4 Download Person/Animal Images from COCO

COCO is a massive dataset with 330,000 images. We only need the classes we care about.

```python
# Save as download_coco.py
"""Download COCO images for person, cow, dog, bird classes only."""
import os
import urllib.request
import json
import shutil

COCO_URL = "http://images.cocodataset.org"
ANN_URL = f"{COCO_URL}/annotations/annotations_trainval2017.zip"

print("This will download ~1 GB of annotations + images. Continue? (y/n)")
if input().lower() != 'y':
    exit()

# Download annotations
os.makedirs("coco", exist_ok=True)
print("Downloading COCO annotations (241 MB)...")
urllib.request.urlretrieve(ANN_URL, "coco/annotations.zip")

print("Extracting...")
shutil.unpack_archive("coco/annotations.zip", "coco/")

# Parse and filter for our classes
with open("coco/annotations/instances_train2017.json") as f:
    coco = json.load(f)

# COCO class IDs we want:
# 1=person, 18=dog, 20=bird, 21=cow
WANTED_COCO_IDS = {1, 18, 20, 21}
COCO_TO_NAME = {1: "person", 18: "dog", 20: "bird", 21: "cow"}

# Find images containing our classes
image_ids = set()
for ann in coco["annotations"]:
    if ann["category_id"] in WANTED_COCO_IDS:
        image_ids.add(ann["image_id"])

# Limit to 3000 images (enough for training)
image_ids = list(image_ids)[:3000]

print(f"Found {len(image_ids)} images with person/cow/dog/bird")
print("Downloading images (this takes 20-30 minutes)...")

os.makedirs("coco/images", exist_ok=True)
for i, img_info in enumerate(coco["images"]):
    if img_info["id"] in image_ids:
        url = f"{COCO_URL}/train2017/{img_info['file_name']}"
        path = f"coco/images/{img_info['file_name']}"
        if not os.path.exists(path):
            try:
                urllib.request.urlretrieve(url, path)
            except Exception:
                pass
        if (i + 1) % 100 == 0:
            print(f"  Downloaded {i+1} images...")

print("COCO subset download complete!")
print("Next step: Convert COCO annotations to YOLO format (Step 5)")
```

```bash
python download_coco.py
# This takes 20-30 minutes — go have tea ☕
```

### 2.5 What You Should Have Now

```
~/farm-training/datasets/downloaded/
├── snakes/          (~2,000 images with labels)
│   ├── train/images/
│   ├── train/labels/
│   ├── valid/images/
│   └── valid/labels/
├── scorpions/       (~1,500 images with labels)
├── fire/            (~2,000 images with labels)
├── smoke/           (~1,500 images with labels)
└── coco/            (~3,000 images — need to convert labels)
    ├── images/
    └── annotations/
```

---

## Step 3: Record Your Own Farm Images

Public datasets give you volume, but your model needs to see YOUR farm — your soil color, your lighting, your camera angles.

### 3.1 Install Cameras and Record for 7 Days

Mount all cameras in their final positions. Then run this on your Edge Node:

```bash
# Create a recording script — save as record_cameras.sh
#!/bin/bash

CAMERAS=(
    "cam-01|rtsp://admin:farm2024@192.168.1.201:554/stream2"
    "cam-02|rtsp://admin:farm2024@192.168.1.202:554/stream2"
    "cam-03|rtsp://admin:farm2024@192.168.1.203:554/stream2"
    "cam-04|rtsp://admin:farm2024@192.168.1.204:554/stream2"
    "cam-05|rtsp://admin:farm2024@192.168.1.205:554/stream2"
    "cam-06|rtsp://admin:farm2024@192.168.1.206:554/stream2"
    "cam-07|rtsp://admin:farm2024@192.168.1.207:554/stream2"
    "cam-08|rtsp://admin:farm2024@192.168.1.208:554/stream2"
)

for entry in "${CAMERAS[@]}"; do
    IFS='|' read -r cam_id rtsp_url <<< "$entry"
    mkdir -p "/data/collection/$cam_id"
    
    echo "Starting recording for $cam_id..."
    ffmpeg -rtsp_transport tcp -i "$rtsp_url" \
        -vf fps=1 -q:v 2 -strftime 1 \
        "/data/collection/$cam_id/frame_%Y%m%d_%H%M%S.jpg" &
done

echo "All cameras recording at 1 FPS. Let it run for 7 days."
echo "Press Ctrl+C to stop all recordings."
wait
```

```bash
chmod +x record_cameras.sh
./record_cameras.sh
# Let this run for 7 days, then stop with Ctrl+C
```

### 3.2 Select Good Frames (2 Hours of Work)

After 7 days you'll have ~80,000 frames per camera. You DON'T need all of them. Select about 50-100 frames per camera that show different conditions:

```bash
# Create selection folder
mkdir -p ~/farm-training/datasets/farm_onsite/images

# Open the frame folder in a file browser
# On Ubuntu: nautilus /data/collection/cam-01/

# LOOK FOR and COPY these to farm_onsite/images/:
# ✓ Early morning (5-6 AM) — warm orange light
# ✓ Bright midday — harsh shadows
# ✓ Cloudy day — flat lighting  
# ✓ Evening golden hour — long shadows
# ✓ Night (IR mode) — grayscale
# ✓ Rain — wet ground, reflections
# ✓ Workers walking through — we need person examples
# ✓ Cattle in pen — we need cow examples
# ✓ Cattle outside pen — test containment detection
# ✓ Empty scenes — IMPORTANT: teaches model "nothing is here"
# ✓ Dogs walking around — prevents dog/person confusion
# ✓ Birds on ground — prevents bird/scorpion confusion

# Target: 50-100 frames per camera = 400-800 total on-site images
```

### 3.3 Stage Fake Hazards (1 Hour)

For snakes, scorpions, and fire — you can't wait for real ones. Stage them:

```
SNAKE: Buy 3-4 realistic rubber snakes (₹200-300 on Amazon)
  - Place them on the ground at different spots in camera view
  - Coiled, stretched, S-curved positions
  - On soil, on rocks, near walls, in grass
  - Take 20-30 photos from each camera that covers snake zones

SCORPION: Print 5-6 high-quality scorpion images on paper, cut them out
  - Place on ground at camera level
  - Different sizes (5cm, 8cm)
  - Against different backgrounds (soil, concrete, leaf litter)
  - Take 10-20 photos per camera

FIRE: Build a small controlled fire in a metal barrel (SUPERVISED!)
  - Let it run for 5 minutes with camera recording
  - Captures flames at your exact camera angle
  - Also let it smoke without flame — captures smoke

CHILDREN: Photograph workers' children (WITH CONSENT)
  - Walking near the pond area
  - Standing, running, sitting
  - Different clothing and distances
  - 20-30 frames from the pond camera
```

---

## Step 4: Label All Images (Drawing Boxes)

Every image needs boxes drawn around each object. This is the most tedious step but the most important — the model learns ONLY from your labels.

### 4.1 Set Up CVAT (Free Labeling Tool)

```bash
# Start CVAT (runs in Docker)
cd ~/farm-training
git clone https://github.com/cvat-ai/cvat.git
cd cvat
docker compose up -d

# Wait 2-3 minutes for it to start
# Open in browser: http://localhost:8080
# Create an admin account when prompted
```

### 4.2 Create Your Project

1. Click **Projects** → **+** (Create Project)
2. Name: `SudarshanChakra v1`
3. Under **Labels**, add these exactly (spelling matters!):
   ```
   person
   child
   cow
   snake
   scorpion
   fire
   smoke
   dog
   vehicle
   bird
   ```
4. Click **Submit**

### 4.3 Upload Your Images

1. Inside the project, click **+** (Create Task)
2. Name: `Farm On-Site Images`
3. Click **Select files** → upload your on-site farm images
4. Click **Submit**
5. Repeat for each dataset (snakes, scorpions, fire, etc.)

**Note:** Roboflow datasets already have labels — you only need to label your ON-SITE images and any COCO images manually.

### 4.4 How to Draw Labels

1. Click on a task → click **Job #1** → opens the annotation editor
2. Select the **Rectangle** tool (shortcut: `N`)
3. For each object in the image:
   - Draw a tight box around it
   - Select the class name from the dropdown
   - Press `N` to start the next box

**Labeling rules:**
```
PERSON:    Box from head to feet. Include arms if extended.
CHILD:     Same as person, but for anyone who looks under 12 years old.
COW:       Box around the whole body including legs and head.
SNAKE:     Box around the entire visible body (often curved/coiled).
SCORPION:  Tight box around the whole body including tail and pincers.
FIRE:      Box around visible flames only (not glow or reflection).
SMOKE:     Box around the dense smoke mass (not thin wisps).
DOG:       Box around the whole body.
VEHICLE:   Box around the whole vehicle.
BIRD:      Box around the whole bird.
```

**How long will this take?**
- On-site images: ~400-800 images × 30 seconds each = 4-8 hours
- Take breaks! Do 100 images, then rest.
- Roboflow datasets already have labels — skip those.

### 4.5 Export Labels from CVAT

After labeling all images:

1. Go to your Task
2. Click **⋮** (three dots) → **Export task dataset**
3. Format: **YOLO 1.1**
4. Click **OK** → downloads a ZIP file
5. Unzip into `~/farm-training/datasets/farm_onsite/`

The ZIP contains:
```
obj_train_data/        ← images
obj_train_data/*.txt   ← label files (one per image)
```

---

## Step 5: Organize All Files

Now combine everything into one training dataset.

### 5.1 Run the Merge Script

```python
# Save as merge_datasets.py
"""Merge all downloaded and on-site datasets into one unified dataset."""
import os
import shutil
import random
import glob

# Where everything goes
OUTPUT = os.path.expanduser("~/farm-training/datasets/farm_combined")
os.makedirs(f"{OUTPUT}/train/images", exist_ok=True)
os.makedirs(f"{OUTPUT}/train/labels", exist_ok=True)
os.makedirs(f"{OUTPUT}/val/images", exist_ok=True)
os.makedirs(f"{OUTPUT}/val/labels", exist_ok=True)

# Class ID mapping — EVERY dataset must use these IDs
CLASS_MAP = {
    "person": 0, "child": 1, "cow": 2, "snake": 3,
    "scorpion": 4, "fire": 5, "smoke": 6,
    "dog": 7, "vehicle": 8, "car": 8, "truck": 8,
    "bird": 9,
}

def copy_dataset(name, src_images, src_labels, remap=None):
    """Copy a dataset's images and labels into the combined folder."""
    images = glob.glob(os.path.join(src_images, "*.jpg")) + \
             glob.glob(os.path.join(src_images, "*.png"))
    
    print(f"Processing {name}: {len(images)} images")
    
    for img_path in images:
        basename = os.path.splitext(os.path.basename(img_path))[0]
        label_path = os.path.join(src_labels, f"{basename}.txt")
        
        if not os.path.exists(label_path):
            continue
        
        # Unique name to avoid conflicts
        new_name = f"{name}_{basename}"
        
        # 85% train, 15% val
        split = "train" if random.random() < 0.85 else "val"
        
        shutil.copy2(img_path, f"{OUTPUT}/{split}/images/{new_name}.jpg")
        
        # Copy label (with optional class ID remapping)
        if remap:
            remap_and_copy_label(label_path, f"{OUTPUT}/{split}/labels/{new_name}.txt", remap)
        else:
            shutil.copy2(label_path, f"{OUTPUT}/{split}/labels/{new_name}.txt")

def remap_and_copy_label(src, dst, remap_dict):
    """Copy a label file, remapping class IDs."""
    lines = []
    with open(src) as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) >= 5:
                old_id = int(parts[0])
                if old_id in remap_dict:
                    parts[0] = str(remap_dict[old_id])
                    lines.append(" ".join(parts))
    with open(dst, "w") as f:
        f.write("\n".join(lines) + "\n" if lines else "")

# ── Copy each dataset ──
DL = os.path.expanduser("~/farm-training/datasets/downloaded")

# Snakes (Roboflow — check their class ID, usually 0=snake)
if os.path.exists(f"{DL}/snakes/train"):
    copy_dataset("snakes",
        f"{DL}/snakes/train/images", f"{DL}/snakes/train/labels",
        remap={0: 3})  # Roboflow snake class 0 → our class 3

# Scorpions
if os.path.exists(f"{DL}/scorpions/train"):
    copy_dataset("scorpions",
        f"{DL}/scorpions/train/images", f"{DL}/scorpions/train/labels",
        remap={0: 4})  # → our class 4

# Fire
if os.path.exists(f"{DL}/fire/train"):
    copy_dataset("fire",
        f"{DL}/fire/train/images", f"{DL}/fire/train/labels",
        remap={0: 5})  # → our class 5

# Smoke
if os.path.exists(f"{DL}/smoke/train"):
    copy_dataset("smoke",
        f"{DL}/smoke/train/images", f"{DL}/smoke/train/labels",
        remap={0: 6})  # → our class 6

# Farm on-site (already has correct class IDs from CVAT export)
if os.path.exists(f"{DL}/../farm_onsite"):
    onsite = f"{DL}/../farm_onsite"
    img_dir = f"{onsite}/obj_train_data" if os.path.exists(f"{onsite}/obj_train_data") else f"{onsite}/images"
    lbl_dir = img_dir  # YOLO labels are in same folder as images
    copy_dataset("onsite", img_dir, lbl_dir)

# Count results
for split in ["train", "val"]:
    count = len(glob.glob(f"{OUTPUT}/{split}/images/*"))
    print(f"{split}: {count} images")

print(f"\nDataset ready at: {OUTPUT}")
print("Next step: Create data.yaml and start training!")
```

```bash
cd ~/farm-training
python merge_datasets.py
```

### 5.2 Create the data.yaml File

```bash
# Create data.yaml — this tells YOLO where your data is
cat > ~/farm-training/datasets/farm_combined/data.yaml << 'EOF'
# SudarshanChakra YOLOv8 Training Dataset
path: /home/$USER/farm-training/datasets/farm_combined
train: train/images
val: val/images

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
EOF

# IMPORTANT: Replace $USER with your actual username
sed -i "s|\$USER|$USER|g" ~/farm-training/datasets/farm_combined/data.yaml

echo "data.yaml created! Contents:"
cat ~/farm-training/datasets/farm_combined/data.yaml
```

### 5.3 Verify Your Dataset

```bash
# Quick check — make sure images and labels match
cd ~/farm-training

python3 -c "
import glob, os

ds = os.path.expanduser('~/farm-training/datasets/farm_combined')
for split in ['train', 'val']:
    images = set(os.path.splitext(f)[0] for f in os.listdir(f'{ds}/{split}/images'))
    labels = set(os.path.splitext(f)[0] for f in os.listdir(f'{ds}/{split}/labels'))
    matched = images & labels
    orphan_imgs = images - labels
    orphan_lbls = labels - images
    print(f'{split}:')
    print(f'  Images: {len(images)}, Labels: {len(labels)}, Matched: {len(matched)}')
    if orphan_imgs:
        print(f'  ⚠ {len(orphan_imgs)} images without labels (will be treated as negatives)')
    if orphan_lbls:
        print(f'  ⚠ {len(orphan_lbls)} labels without images (will be ignored)')
    print()
"
```

You should see something like:
```
train:
  Images: 8500, Labels: 8500, Matched: 8500

val:
  Images: 1500, Labels: 1500, Matched: 1500
```

---

## Step 6: Train the Model

This is where the GPU does the heavy lifting. You start the command, and the PC trains for 8-12 hours.

### 6.1 Start Training

```bash
cd ~/farm-training
source venv/bin/activate

# THE TRAINING COMMAND — copy-paste this exactly
yolo detect train \
    model=yolov8n.pt \
    data=datasets/farm_combined/data.yaml \
    epochs=300 \
    imgsz=640 \
    batch=16 \
    patience=50 \
    device=0 \
    workers=8 \
    project=runs \
    name=farm_v1 \
    exist_ok=True
```

**What each setting means:**
```
model=yolov8n.pt      Start from a pre-trained base (knows person, car, dog already)
data=...data.yaml     Points to your dataset
epochs=300            Train for up to 300 rounds (stops early if not improving)
imgsz=640             Image size: 640×640 pixels
batch=16              Process 16 images at a time (reduce to 8 if GPU runs out of memory)
patience=50           Stop early if no improvement for 50 rounds
device=0              Use GPU #0
workers=8             Use 8 CPU threads to load data
project=runs          Save results in runs/ folder
name=farm_v1          Name this training run "farm_v1"
```

### 6.2 What to Expect

```
Training starts — you'll see output like:

Epoch    GPU_mem   box_loss  cls_loss  dfl_loss  Instances  Size
1/300      4.2G     1.234     2.345     1.567        45     640
2/300      4.2G     1.198     2.123     1.432        52     640
3/300      4.2G     1.156     1.987     1.398        38     640
...

The numbers (losses) should go DOWN over time. That means the model is learning.

After each epoch, it also shows validation results:
  Class      Images  Instances    P    R    mAP50    mAP50-95
  all          1500      3200  0.72  0.68    0.71       0.49
  person        450       890  0.85  0.82    0.84       0.62
  snake         200       230  0.65  0.58    0.63       0.41
  ...

P = Precision (how many of its detections are correct)
R = Recall (how many real objects it finds)
mAP50 = Main accuracy metric (higher = better, target: >0.78)
```

### 6.3 Common Issues During Training

```
PROBLEM: "CUDA out of memory"
FIX:     Change batch=16 to batch=8 (or even batch=4)

PROBLEM: Training seems stuck (losses not decreasing)
FIX:     This is normal for the first 10-20 epochs. Wait.

PROBLEM: mAP50 is very low (<0.30) after 100 epochs
FIX:     Check your labels — there might be wrong class IDs.
         Run the verify script from Step 5.3 again.

PROBLEM: Computer froze
FIX:     batch size too high for your GPU. Reduce batch=8 or batch=4.
         Training will automatically resume from last checkpoint if you restart.
```

### 6.4 Training Takes 8-12 Hours

**You can safely close the terminal and the training continues** if you use `screen` or `tmux`:

```bash
# Start a screen session (persists after disconnect)
screen -S training

# Run the training command (from Step 6.1)
yolo detect train ...

# Detach from screen: press Ctrl+A then D
# Re-attach later: screen -r training
```

**Go to sleep.** Check in the morning.

---

## Step 7: Check If the Model Is Good

### 7.1 Find Your Results

After training completes, your results are at:

```bash
ls ~/farm-training/runs/farm_v1/

# You'll see:
# weights/
#   best.pt         ← THE BEST MODEL (this is what you deploy)
#   last.pt         ← The model at the last epoch
# results.csv       ← Training metrics over time
# confusion_matrix.png   ← Shows what the model confuses
# results.png       ← Training curves graph
# val_batch0_pred.jpg    ← Example detections on validation images
```

### 7.2 Look at the Results

```bash
# Open the results image (shows training curves)
xdg-open ~/farm-training/runs/farm_v1/results.png

# Open confusion matrix (shows what gets confused with what)
xdg-open ~/farm-training/runs/farm_v1/confusion_matrix.png

# Open example predictions
xdg-open ~/farm-training/runs/farm_v1/val_batch0_pred.jpg
```

### 7.3 Check the Numbers

```bash
# Run validation on the best model
cd ~/farm-training
source venv/bin/activate

yolo detect val \
    model=runs/farm_v1/weights/best.pt \
    data=datasets/farm_combined/data.yaml \
    imgsz=640
```

**What to look for:**

```
Target metrics (minimum acceptable):
  person:    mAP50 > 0.85   ✓ good  ✗ needs more person images
  child:     mAP50 > 0.70   ✓ good  ✗ needs more child images
  cow:       mAP50 > 0.88   ✓ good  ✗ needs more cow images
  snake:     mAP50 > 0.72   ✓ good  ✗ needs more snake images
  scorpion:  mAP50 > 0.65   ✓ good  ✗ needs more scorpion images
  fire:      mAP50 > 0.82   ✓ good  ✗ needs more fire images
  smoke:     mAP50 > 0.75   ✓ good  ✗ needs more smoke images
  Overall:   mAP50 > 0.78   ✓ READY TO DEPLOY  ✗ keep improving
```

### 7.4 What If Numbers Are Bad?

```
IF snake mAP50 is low (<0.60):
  → Need more snake images. Download another snake dataset from Roboflow.
  → Stage more rubber snakes on your farm and photograph them.
  → Re-merge datasets and retrain.

IF person mAP50 is low (<0.70):
  → Check if your on-site person labels are accurate.
  → Add more on-site images with workers at different distances.

IF everything is low (<0.50):
  → Your labels might have wrong class IDs.
  → Open some label files and check:
    cat datasets/farm_combined/train/labels/some_image.txt
  → Each line should be: class_id x_center y_center width height
  → class_id must match data.yaml (0=person, 3=snake, etc.)
```

### 7.5 Test on Your Own Images

```bash
# Test on a real farm camera frame
yolo detect predict \
    model=runs/farm_v1/weights/best.pt \
    source=/path/to/a/farm/frame.jpg \
    imgsz=640 \
    conf=0.40 \
    save=True

# Results saved in runs/detect/predict/
xdg-open runs/detect/predict/farm_frame.jpg
# You should see bounding boxes drawn on detected objects
```

---

## Step 8: Convert for Fast Inference (TensorRT)

The trained model (.pt) works but is slow. Converting to TensorRT makes it 3× faster.

**This step MUST be done on the actual Edge Node GPU (RTX 3060)** because TensorRT engines are GPU-specific.

### 8.1 Copy Model to Edge Node

```bash
# From your training PC:
scp ~/farm-training/runs/farm_v1/weights/best.pt \
    user@edge-node-a:/path/to/SudarshanChakra/edge/models/yolov8n_farm.pt

# Also copy to Node B:
scp ~/farm-training/runs/farm_v1/weights/best.pt \
    user@edge-node-b:/path/to/SudarshanChakra/edge/models/yolov8n_farm.pt
```

### 8.2 Convert on Edge Node

```bash
# SSH into Edge Node A
ssh user@edge-node-a
cd /path/to/SudarshanChakra/edge

# Activate environment (or use Docker)
source venv/bin/activate  # or: docker exec -it edge-ai bash

# Export to TensorRT FP16
yolo export \
    model=models/yolov8n_farm.pt \
    format=engine \
    half=True \
    device=0

# This takes 2-3 minutes
# Creates: models/yolov8n_farm.engine
```

### 8.3 Verify Speed

```bash
yolo detect predict \
    model=models/yolov8n_farm.engine \
    source=test_image.jpg \
    imgsz=640

# Should show: Speed: 1.5ms pre-process, 6.2ms inference, 1.0ms postprocess
# If inference is <10ms, you're good!
```

---

## Step 9: Deploy to Edge Nodes

### 9.1 Restart the Edge Container

```bash
# On Edge Node A:
cd /path/to/SudarshanChakra/edge
docker compose restart edge-ai

# Check logs:
docker logs -f edge-ai

# You should see:
# Loading cached TensorRT engine: /app/models/yolov8n_farm.engine
# Model loaded successfully.
# Starting inference pipeline with 8 cameras...
```

### 9.2 Verify It's Working

```bash
# Open the Edge GUI in a browser:
# http://edge-node-a-ip:5000

# You should see:
# - Camera feeds updating
# - Detection boxes drawn on objects
# - Alerts appearing in the alert feed
```

### 9.3 Repeat for Edge Node B

Same steps: copy model, export to TensorRT, restart container.

---

## What's Next?

After 2 weeks of running the model on the farm:

1. **Check false positives:** Are there alerts that shouldn't be alerts?
   - Mark them as "false_positive" in the dashboard
   - Save those images → add them to your training set with correct labels
   - Retrain (go back to Step 6 with the expanded dataset)

2. **Check missed detections:** Are there objects the model misses?
   - Save those frames → label them → add to training set
   - Retrain

3. **Seasonal changes:** Retrain every 3-6 months as vegetation, lighting, and conditions change.

---

## Quick Reference Card

```
SETUP:           pip install ultralytics
DOWNLOAD DATA:   python download_snakes.py (repeat for each class)
LABEL:           CVAT at http://localhost:8080
MERGE:           python merge_datasets.py
TRAIN:           yolo detect train model=yolov8n.pt data=data.yaml epochs=300
VALIDATE:        yolo detect val model=runs/farm_v1/weights/best.pt data=data.yaml
TEST:            yolo detect predict model=best.pt source=image.jpg
EXPORT:          yolo export model=best.pt format=engine half=True
DEPLOY:          Copy .engine to edge/models/ → docker compose restart
```
