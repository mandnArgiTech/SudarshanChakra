#!/usr/bin/env python3
"""
Skeleton CLI to prepare a YOLO-style dataset directory from recordings.

Usage:
  python3 edge/scripts/export_training_data.py --input /path/to/mp4s_or_frames --output ./dataset

Creates ``output/images/`` (copied or linked inputs) and ``output/labels/`` (empty stub) plus README.
Full frame extraction and labeling is deferred to a future iteration.

Example:
  python3 scripts/export_training_data.py -i /data/video/cam-01/2026-03-01 -o ~/training/run1
"""

from __future__ import annotations

import argparse
import os
import shutil
import sys


def main() -> int:
    p = argparse.ArgumentParser(description="Export training data skeleton (images + empty labels/).")
    p.add_argument("--input", "-i", required=True, help="Directory of MP4s or image frames")
    p.add_argument("--output", "-o", required=True, help="Output dataset root")
    args = p.parse_args()

    src = os.path.abspath(args.input)
    out = os.path.abspath(args.output)
    if not os.path.isdir(src):
        print(f"Input is not a directory: {src}", file=sys.stderr)
        return 1

    images_dir = os.path.join(out, "images")
    labels_dir = os.path.join(out, "labels")
    os.makedirs(images_dir, exist_ok=True)
    os.makedirs(labels_dir, exist_ok=True)

    copied = 0
    for name in sorted(os.listdir(src)):
        path = os.path.join(src, name)
        if not os.path.isfile(path):
            continue
        low = name.lower()
        if low.endswith((".mp4", ".avi", ".mkv", ".jpg", ".jpeg", ".png")):
            dest = os.path.join(images_dir, name)
            shutil.copy2(path, dest)
            copied += 1

    readme = os.path.join(out, "README.txt")
    with open(readme, "w", encoding="utf-8") as f:
        f.write(
            "SudarshanChakra training export (skeleton)\n"
            "==========================================\n"
            "- images/: copied video files and/or stills from --input\n"
            "- labels/: empty; add YOLO .txt label files matching basenames\n"
            "Next steps: ffmpeg frame extraction, labeling tool, data.yaml for ultralytics train.\n"
        )

    print(f"Wrote {copied} file(s) under {images_dir}; labels/ is empty. See {readme}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
