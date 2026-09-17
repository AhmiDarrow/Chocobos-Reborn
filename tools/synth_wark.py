"""Original chocobo calls, synthesised (no Square Enix audio).

A chocobo's "kweh!" / "wark!" is a parrot-style squawk: a bright voice that
jumps up to ~1.3 kHz, holds with a fast warble (~40-60 Hz trill), rasps in the
middle and falls off at the end, with a clicky "k" on the front. v2 is an
additive syrinx voice (harmonics + trill + tremolo + period-doubling rasp + one
nasal body resonance) instead of the v1 glottal-pulse / vowel-formant synth,
which sounded like a talking synthesiser rather than a bird. Never sampled from
Square Enix audio. Five variants for the game's sound events.

    python tools/synth_wark.py        # writes sounds/entity/chocobo/*.ogg (ffmpeg)
    python tools/synth_wark.py --wav  # keep .wav next to them for listening
"""
from __future__ import annotations

import math
import shutil
import subprocess
import sys
import wave
from pathlib import Path

import numpy as np
from scipy import signal

ROOT = Path(__file__).resolve().parents[1]
SND = ROOT / "src/main/resources/assets/chocobosreborn/sounds/entity/chocobo"
RATE = 44100


def env_adsr(n, a, d, s_level, r, hold=None):
    t = np.arange(n) / RATE
    total = n / RATE
    e = np.ones(n)
    e = np.where(t < a, t / max(a, 1e-4), e)
    dec = (t >= a) & (t < a + d)
    e = np.where(dec, 1.0 - (1.0 - s_level) * (t - a) / max(d, 1e-4), e)
    sus = t >= a + d
    e = np.where(sus, s_level, e)
    rel = t > total - r
    e = np.where(rel, e * np.clip((total - t) / max(r, 1e-4), 0, 1), e)
    return e


def pulse_train(f0, rate=RATE, open_q=0.35):
    """Glottal-ish pulses: integrate phase, emit a short asymmetric pulse per cycle."""
    phase = np.cumsum(f0 / rate) % 1.0
    # Rosenberg-style pulse: rising quadratic then sharp close, within open_q of the cycle
    x = phase / open_q
    pulse = np.where(phase < open_q, 3 * x ** 2 - 2 * x ** 3, 0.0)
    pulse = np.diff(pulse, prepend=0.0)  # derivative -> buzzy source with strong harmonics
    return pulse / (np.max(np.abs(pulse)) + 1e-9)


def formant_filter(src, tracks, bw=(90.0, 140.0, 220.0), frame=256):
    """tracks: list of arrays (per sample) of formant centre frequencies."""
    out = np.zeros_like(src)
    n = len(src)
    states = [None] * len(tracks)
    for start in range(0, n, frame):
        end = min(n, start + frame)
        seg = src[start:end]
        acc = np.zeros_like(seg)
        for k, track in enumerate(tracks):
            fc = float(np.mean(track[start:end]))
            q = fc / bw[k]
            b, a = signal.iirpeak(fc, q, fs=RATE)
            if states[k] is None:
                states[k] = signal.lfilter_zi(b, a) * seg[0]
            y, states[k] = signal.lfilter(b, a, seg, zi=states[k])
            gain = [1.0, 0.7, 0.35][k]
            acc += gain * y
        out[start:end] = acc
    return out


def lerp_track(n, points):
    """points: [(time_s, value), ...] -> per-sample linear track."""
    t = np.arange(n) / RATE
    ts = [p[0] for p in points]
    vs = [p[1] for p in points]
    return np.interp(t, ts, vs)


def harmonic_voice(f0, amps, rate=RATE):
    """Additive bird voice: phase-continuous harmonics with fixed relative amplitudes.
    A syrinx is closer to a whistle with a few strong overtones than to a buzzy
    glottal pulse train, which is what made the first version sound like a synth."""
    phase = 2 * np.pi * np.cumsum(f0 / rate)
    out = np.zeros_like(f0)
    for k, a in enumerate(amps, start=1):
        out += a * np.sin(k * phase)
    return out / (np.sum(np.abs(amps)) + 1e-9)


def squawk(duration, f0_points, f1_points=None, f2_points=None, f3_points=None, *, vib_hz=48.0, vib_depth=0.07,
           rasp=0.25, breath=0.05, click=0.5, attack=0.012, release=0.09, seed=1,
           amps=(1.0, 0.8, 0.55, 0.35, 0.22, 0.14, 0.09, 0.06), body=(1900.0, 0.5), trem_hz=None):
    """A parrot-ish squawk: additive voice with a fast trill (vib_hz, the warble
    that makes a chocobo cry sound like a chocobo), a chaotic rasp (period-
    doubled sub-harmonic), a nasal body resonance, a "k" click at the onset and a
    pitch fall into the release. f1..f3 are kept for signature compatibility but
    the vowel colour now comes from `amps` + `body`."""
    rng = np.random.default_rng(seed)
    n = int(RATE * duration)
    t = np.arange(n) / RATE
    f0 = lerp_track(n, f0_points)
    # fast trill (bird warble) that itself wobbles a little, plus slow drift
    trill = np.sin(2 * np.pi * vib_hz * t * (1.0 + 0.08 * np.sin(2 * np.pi * 3.0 * t)) + 0.7)
    f0 = f0 * (1.0 + vib_depth * trill)
    f0 = f0 * (1.0 + 0.010 * rng.standard_normal(n).cumsum() / np.sqrt(np.arange(1, n + 1)))
    voice = harmonic_voice(f0, amps)
    # rasp: period-doubling sub-harmonic, strongest in the middle of the call
    sub = harmonic_voice(f0 / 2.0, (0.0, 1.0, 0.0, 0.6, 0.0, 0.3))
    rasp_env = np.sin(np.pi * np.clip(t / duration, 0, 1)) ** 0.5
    voice = voice * (1.0 - rasp * rasp_env) + rasp * rasp_env * voice * (0.5 + 0.5 * np.sign(sub + 1e-9))
    # tremolo at the trill rate gives the "squeaky toy" pulsing
    th = trem_hz or vib_hz
    voice *= 1.0 - 0.25 * (0.5 + 0.5 * np.sin(2 * np.pi * th * t))
    voice += breath * rng.standard_normal(n) * (0.4 + 0.6 * np.linspace(0, 1, n))
    # nasal body: one resonance around 1.5-2.5 kHz, mixed in
    fc, mix = body
    b, a = signal.iirpeak(fc, fc / 350.0, fs=RATE)
    voice = (1.0 - mix) * voice + mix * signal.lfilter(b, a, voice)
    # onset click: short high-passed noise burst ("k")
    nc = int(RATE * 0.016)
    burst = rng.standard_normal(nc) * np.linspace(1, 0, nc) ** 2
    b, a = signal.butter(2, [1800 / (RATE / 2), 6000 / (RATE / 2)], btype="band")
    burst = signal.lfilter(b, a, burst)
    voice = voice / (np.max(np.abs(voice)) + 1e-9)
    out = voice * env_adsr(n, attack, 0.05, 0.85, release)
    b, a = signal.butter(1, 500 / (RATE / 2), btype="high")
    out = signal.lfilter(b, a, out)
    out = np.tanh(1.6 * out / (np.max(np.abs(out)) + 1e-9)) * 0.9
    out[:nc] += click * burst / (np.max(np.abs(burst)) + 1e-9) * 0.35
    return np.clip(out, -1, 1)


def two_syllable(a, b, gap=0.045):
    g = np.zeros(int(RATE * gap))
    return np.concatenate([a, g, b])


def calls():
    # "kweh!" — the default: quick rise, trilled hold around 1.3 kHz, fall.
    kweh = squawk(0.42,
                  f0_points=[(0, 700), (0.04, 1250), (0.10, 1400), (0.28, 1300), (0.36, 1050), (0.42, 750)],
                  vib_hz=52, vib_depth=0.07, rasp=0.28, body=(2100.0, 0.5), seed=3)
    # "wark!" — lower and rounder, slower trill, harder fall.
    wark = squawk(0.50,
                  f0_points=[(0, 600), (0.05, 1000), (0.12, 1120), (0.30, 1050), (0.42, 820), (0.50, 560)],
                  vib_hz=38, vib_depth=0.08, rasp=0.32, body=(1600.0, 0.55), release=0.13, seed=5,
                  amps=(1.0, 0.9, 0.6, 0.45, 0.3, 0.2, 0.12, 0.08))
    # follow: eager double "kweh-kweh", second one higher
    follow = two_syllable(
        squawk(0.22, [(0, 800), (0.04, 1300), (0.14, 1350), (0.22, 950)], vib_hz=55, rasp=0.2, seed=7),
        squawk(0.26, [(0, 900), (0.04, 1450), (0.16, 1500), (0.26, 1000)], vib_hz=58, rasp=0.2, seed=8))
    # stay: settled, low, longer, soft onset
    stay = squawk(0.55, [(0, 550), (0.08, 900), (0.30, 880), (0.55, 480)], vib_hz=30, vib_depth=0.06, rasp=0.3,
                  click=0.2, attack=0.03, release=0.18, body=(1500.0, 0.55), seed=11,
                  amps=(1.0, 0.9, 0.6, 0.45, 0.3, 0.2, 0.12, 0.08))
    # wander: quiet short chirp
    wander = squawk(0.18, [(0, 1000), (0.03, 1500), (0.10, 1450), (0.18, 1100)], vib_hz=60, vib_depth=0.05,
                    rasp=0.12, click=0.35, breath=0.03, seed=13) * 0.7
    return {"kweh": kweh, "kweh_follow": follow, "kweh_stay": stay, "kweh_wander": wander, "wark": wark}


def write_wav(path: Path, data: np.ndarray):
    path.parent.mkdir(parents=True, exist_ok=True)
    pcm = (np.clip(data, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "w") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(RATE)
        wf.writeframes(pcm.tobytes())


def main():
    keep_wav = "--wav" in sys.argv
    ffmpeg = shutil.which("ffmpeg")
    for name, data in calls().items():
        wav = SND / f"{name}.wav"
        write_wav(wav, data)
        if ffmpeg:
            subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "5",
                            str(wav.with_suffix(".ogg"))], check=True)
            if not keep_wav:
                wav.unlink()
        print("wrote", name, f"{len(data) / RATE:.2f}s")
    if not ffmpeg:
        print("ffmpeg missing: .wav written, convert to .ogg before shipping")


if __name__ == "__main__":
    main()
