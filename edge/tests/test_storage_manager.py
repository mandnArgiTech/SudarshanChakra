"""storage_manager helper behavior."""

from __future__ import annotations

import os
from pathlib import Path

from storage_manager import StorageManager


def test_list_day_folders_ordering(tmp_path: Path):
    # cam-a / 2026-03-01, cam-b / 2026-03-02
    for cam, day in [("cam-a", "2026-03-01"), ("cam-b", "2026-03-02")]:
        d = tmp_path / cam / day
        d.mkdir(parents=True)
        (d / ".keep").write_text("x", encoding="utf-8")

    folders = StorageManager._list_day_folders(str(tmp_path))
    basenames = [os.path.basename(p) for p in folders]
    assert basenames == ["2026-03-01", "2026-03-02"]
