"""Chocobo Whistle calls, synthesised (original audio, nothing sampled).

A herder's whistle: one bright near-sine tone between ~1.6 and 3 kHz that
glides between pitches, with a little second harmonic, breath noise riding
the tone, a soft chiff on each onset, and a short room tail. Four calls; the
whistle plays them in turn, so blowing it twice never sounds the same.

    python tools/synth_whistle.py        # writes sounds/item/whistle/call_<n>.ogg (ffmpeg)
    python tools/synth_whistle.py --wav  # keep .wav next to them for listening
"""
from __future__ import annotations

import shutil
import subprocess
import sys
import wave
from pathlib import Path

import numpy as np
from scipy import signal

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/chocobosreborn/sounds/item/whistle"
RATE = 44100
RNG = np.random.default_rng(7)

# Each call is a list of (seconds, start Hz, end Hz, level) segments. Segments
# with level 0 are breaths between notes; pitch glides within a segment.
CALLS = {
    # "whee-oo": up, then a long fall
    "call_1": [(0.16, 1650, 2650, 1.0), (0.42, 2650, 1900, 1.0)],
    # "fweet, fweet": two quick rising chirps
    "call_2": [(0.15, 1800, 2800, 1.0), (0.10, 0, 0, 0.0), (0.17, 1850, 2900, 1.0)],
    # "wheeeet": a long climb with vibrato
    "call_3": [(0.62, 1700, 3000, 1.0)],
    # "fwee-oo-whee": high, low, high
    "call_4": [(0.14, 2350, 2450, 1.0), (0.14, 2450, 1800, 0.9), (0.24, 1800, 2750, 1.0)],
}


def glide(seconds: float, f0: float, f1: float) -> np.ndarray:
    n = int(seconds * RATE)
    x = np.linspace(0.0, 1.0, n, endpoint=False)
    shape = 0.5 - 0.5 * np.cos(np.pi * x)          # ease in/out between pitches
    return f0 + (f1 - f0) * shape


def call(segments) -> np.ndarray:
    freq, level, starts = [], [], []
    at, voiced_before = 0, False
    for seconds, f0, f1, lvl in segments:
        n = int(seconds * RATE)
        if lvl == 0:
            freq.append(np.full(n, freq[-1][-1] if freq else 2000.0))
        else:
            freq.append(glide(seconds, f0, f1))
            if not voiced_before:
                starts.append(at)                   # a new breath, not a glide inside one note
        level.append(np.full(n, float(lvl)))
        voiced_before = lvl > 0
        at += n
    freq = np.concatenate(freq)
    level = np.concatenate(level)
    n = len(freq)
    t = np.arange(n) / RATE
    # One envelope per unbroken note: soft 20 ms onset, 60 ms let-go, level changes smoothed.
    rise = signal.lfilter([1 - 0.9988], [1, -0.9988], level)
    fall = signal.lfilter([1 - 0.99962], [1, -0.99962], level[::-1])[::-1]
    amp = np.minimum(rise, fall) / max(level.max(), 1e-9)
    amp = np.clip(amp * 1.15, 0, 1)
    # Vibrato builds over each held note; the lip never holds perfectly still.
    vib = 1.0 + 0.012 * np.sin(2 * np.pi * 6.2 * t) * np.clip(t / 0.25, 0, 1)
    jitter = 1.0 + 0.003 * signal.lfilter([1], [1, -0.999], RNG.standard_normal(n)) / 30
    phase = 2 * np.pi * np.cumsum(freq * vib * jitter) / RATE
    tone = np.sin(phase) + 0.12 * np.sin(2 * phase) + 0.03 * np.sin(3 * phase)
    # Breath noise: a band around the whistle's range that follows the same envelope.
    noise = RNG.standard_normal(n)
    b, a = signal.butter(2, [1500 / (RATE / 2), 4500 / (RATE / 2)], btype="band")
    breath = signal.lfilter(b, a, noise) * 0.05
    # Chiff: a 25 ms puff where each breath starts, never at a glide.
    puff = np.zeros(n)
    for s in starts:
        k = min(int(0.025 * RATE), n - s)
        puff[s:s + k] = np.exp(-np.arange(k) / (0.008 * RATE))
    chiff = puff * signal.lfilter(b, a, noise) * 0.12
    dry = amp * (tone + breath) + chiff
    return room(dry)


def room(dry: np.ndarray) -> np.ndarray:
    """A short, dark outdoor tail so the whistle sits in the world."""
    tail = int(0.45 * RATE)
    ir = RNG.standard_normal(tail) * np.exp(-np.arange(tail) / (0.09 * RATE))
    b, a = signal.butter(2, 3500 / (RATE / 2))
    ir = signal.lfilter(b, a, ir)
    ir[0] = 0.0
    wet = np.zeros(len(dry) + tail)
    full = signal.fftconvolve(dry, ir)
    wet[: len(full)] = full[: len(wet)]
    wet /= np.max(np.abs(wet)) + 1e-9
    out = np.concatenate([dry, np.zeros(tail)]) + 0.18 * wet
    fade = int(0.08 * RATE)
    out[-fade:] *= np.linspace(1, 0, fade)
    return out / (np.max(np.abs(out)) + 1e-9) * 0.89


def write_wav(path: Path, data: np.ndarray):
    pcm = (np.clip(data, -1, 1) * 32767).astype(np.int16)
    with wave.open(str(path), "wb") as wf:
        wf.setnchannels(1)                          # mono, so the game places it in the world
        wf.setsampwidth(2)
        wf.setframerate(RATE)
        wf.writeframes(pcm.tobytes())


def main():
    keep = "--wav" in sys.argv
    ffmpeg = shutil.which("ffmpeg")
    OUT.mkdir(parents=True, exist_ok=True)
    for name, segments in CALLS.items():
        wav = OUT / f"{name}.wav"
        data = call(segments)
        write_wav(wav, data)
        if ffmpeg:
            subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", str(wav), "-vn", "-c:a", "libvorbis", "-q:a", "5",
                            str(wav.with_suffix(".ogg"))], check=True)
            if not keep:
                wav.unlink()
        print(name, f"{len(data) / RATE:.2f} s")
    if not ffmpeg:
        print("ffmpeg missing: .wav written, convert to .ogg before shipping")


if __name__ == "__main__":
    main()
