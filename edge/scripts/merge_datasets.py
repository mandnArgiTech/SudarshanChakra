#!/usr/bin/env python3
"""
merge_datasets.py — Merge multiple YOLOv8 folders into one unified dataset.
Copies images + labels into output/images/{train,val} and labels/{train,val}.
"""
import argparse
import random
import shutil
from pathlib import Path


def collect_split(src: Path, split: str):
    img = src / split / "images"
    lbl = src / split / "labels"
    if not img.is_dir():
        img = src / "images" / split
        lbl = src / "labels" / split
    if not img.is_dir():
        return []
    out = []
    for f in img.glob("*"):
        if f.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp"):
            lf = lbl / (f.stem + ".txt")
            out.append((f, lf if lf.is_file() else None))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sources", nargs="+", required=True, help="YOLO dataset roots")
    ap.add_argument("--output", required=True)
    ap.add_argument("--val-ratio", type=float, default=0.15)
    args = ap.parse_args()
    out = Path(args.output)
    for sp in ("train", "val"):
        (out / "images" / sp).mkdir(parents=True, exist_ok=True)
        (out / "labels" / sp).mkdir(parents=True, exist_ok=True)

    all_items = []
    for s in args.sources:
        root = Path(s)
        for split in ("train", "val"):
            all_items.extend([(a, b, split) for a, b in collect_split(root, split)])
        all_items.extend([(a, b, "train") for a, b in collect_split(root, "")])

    random.shuffle(all_items)
    n_val = max(1, int(len(all_items) * args.val_ratio))
    for i, (img, lbl, _orig) in enumerate(all_items):
        sp = "val" if i < n_val else "train"
        shutil.copy2(img, out / "images" / sp / img.name)
        if lbl and lbl.is_file():
            shutil.copy2(lbl, out / "labels" / sp / (img.stem + ".txt"))
    print(f"Merged {len(all_items)} images into {out}")


if __name__ == "__main__":
    main()
