#!/usr/bin/env python3
"""
download_datasets.py — Pull Roboflow YOLO datasets via API.
Requires: pip install roboflow
Usage:
  export ROBOFLOW_API_KEY=...
  python3 download_datasets.py --project farm-hazards --version 1 --out ./datasets/roboflow
"""
import argparse
import os
import sys


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--api-key", default=os.getenv("ROBOFLOW_API_KEY", ""))
    p.add_argument("--workspace", default=os.getenv("ROBOFLOW_WORKSPACE", ""))
    p.add_argument("--project", required=True)
    p.add_argument("--version", type=int, default=1)
    p.add_argument("--out", default="./datasets/roboflow")
    args = p.parse_args()
    if not args.api_key:
        print("Set ROBOFLOW_API_KEY or --api-key", file=sys.stderr)
        sys.exit(1)
    try:
        from roboflow import Roboflow
    except ImportError:
        print("pip install roboflow", file=sys.stderr)
        sys.exit(1)
    rf = Roboflow(api_key=args.api_key)
    w = rf.workspace(args.workspace) if args.workspace else rf.workspace()
    proj = w.project(args.project)
    ds = proj.version(args.version).download("yolov8", location=args.out)
    print("Downloaded to", ds.location)


if __name__ == "__main__":
    main()
