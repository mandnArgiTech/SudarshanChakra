"""
ALSA capture helper for siren verification (Suite 7). Requires ``arecord`` on the edge host.
"""
from __future__ import annotations

import shutil
import struct
import subprocess
import tempfile
import wave
from pathlib import Path
from typing import Optional


def arecord_available() -> bool:
    return shutil.which("arecord") is not None


def verify_non_silence_wav(wav_path: Path, min_rms: float = 100.0) -> float:
    with wave.open(str(wav_path), "rb") as w:
        frames = w.readframes(w.getnframes())
    if not frames:
        return 0.0
    samples = struct.unpack(f"<{len(frames) // 2}h", frames)
    if not samples:
        return 0.0
    rms = (sum(s * s for s in samples) / len(samples)) ** 0.5
    assert rms > min_rms, f"Silence detected (RMS={rms}), expected audio above {min_rms}"
    return rms


def record_and_measure_rms(device: str = "hw:0,0", duration_sec: int = 3) -> Optional[float]:
    """
    Record from ALSA device and return RMS; None if arecord missing or failed.
    """
    if not arecord_available():
        return None
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = Path(tmp.name)
    try:
        subprocess.run(
            [
                "arecord",
                "-D",
                device,
                "-d",
                str(duration_sec),
                "-f",
                "S16_LE",
                "-r",
                "44100",
                str(path),
            ],
            check=True,
            timeout=duration_sec + 5,
            capture_output=True,
        )
        with wave.open(str(path), "rb") as w:
            frames = w.readframes(w.getnframes())
        if not frames:
            return 0.0
        samples = struct.unpack(f"<{len(frames) // 2}h", frames)
        return (sum(s * s for s in samples) / len(samples)) ** 0.5 if samples else 0.0
    except Exception:
        return None
    finally:
        path.unlink(missing_ok=True)
