#!/usr/bin/env python3
"""Scheduled PA playback — use with systemd timer or cron."""
import os
import sys
import time

try:
    import schedule
except ImportError:
    print("pip install schedule", file=sys.stderr)
    sys.exit(1)

# Import PAController from same package path when run as script
sys.path.insert(0, os.path.dirname(__file__))


def main():
    def job():
        print("scheduled tick — publish play_bgm via MQTT from pa_controller")

    schedule.every().day.at(os.getenv("PA_SCHEDULE_TIME", "06:00")).do(job)
    while True:
        schedule.run_pending()
        time.sleep(30)


if __name__ == "__main__":
    main()
