# Model training runbook (edge YOLO)

**Status:** Operational procedure — **trained weights are not shipped** in this repository. The edge stack ships with guides and default `.pt` placeholders; production accuracy depends on your dataset and training runs.

## What you need

- Labeled images/videos for target classes (person, fire, smoke, snake, cow, etc.) consistent with farm deployment.
- GPU workstation or cloud instance with CUDA for YOLO training (Ultralytics or equivalent).
- Version control for **dataset manifests** and **exported weights** (artifact storage outside git, or Git LFS if policy allows).

## High-level steps

1. **Collect** representative frames from each camera (day/night, weather, occlusions).
2. **Annotate** (bounding boxes) in Roboflow, CVAT, or Label Studio; export YOLO format.
3. **Train** using the same major YOLO version as `edge/requirements.txt` / pipeline code.
4. **Validate** mAP and per-class recall on a held-out set; tune confidence thresholds in edge config.
5. **Deploy** the new `.pt` to edge nodes (replace path in pipeline config / env); restart the edge service.
6. **Regression-test** alert MQTT → cloud path with the Farm Simulator or staging broker.

## Repo pointers

- Edge inference: `edge/pipeline.py`, `edge/farm_edge_node.py`, `edge/config/`.
- Planning context: [VIDEO_STORAGE_AND_CAMERA_LIFECYCLE_PLAN.md](VIDEO_STORAGE_AND_CAMERA_LIFECYCLE_PLAN.md), [VIDEO_PLAYBACK_PLAN.md](VIDEO_PLAYBACK_PLAN.md).

## Acceptance (definition of done for a training cycle)

- [ ] Weights file named/versioned and stored in approved artifact location.
- [ ] Edge node loads weights without startup errors; inference FPS within target.
- [ ] Spot-check: known-positive clips generate alerts; obvious false negatives documented.
