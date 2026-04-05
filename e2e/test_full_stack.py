#!/usr/bin/env python3
"""
Legacy entrypoint — full pytest suite lives under e2e/tests/.

  PYTHONPATH=<repo-root> python3 -m pytest e2e/tests -v
  ./e2e/run_full_e2e.sh --config e2e/config/e2e_config.yml
"""
from __future__ import annotations

import pathlib
import sys

import pytest

if __name__ == "__main__":
    root = pathlib.Path(__file__).resolve().parent
    repo = root.parent
    if str(repo) not in sys.path:
        sys.path.insert(0, str(repo))
    raise SystemExit(pytest.main([str(root / "tests"), "-v"] + sys.argv[1:]))
