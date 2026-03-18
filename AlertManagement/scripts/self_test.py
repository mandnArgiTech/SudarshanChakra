#!/usr/bin/env python3
"""Daily PA self-test: optional beep via speaker-test or espeak."""
import os
import subprocess
import sys


def main():
    if os.getenv("PA_SELF_TEST", "false").lower() != "true":
        print("Set PA_SELF_TEST=true")
        return 0
    try:
        subprocess.run(
            ["espeak-ng", "PA system self test OK"],
            timeout=10, capture_output=True,
        )
    except Exception as e:
        print(e, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
