"""Candidate chocobo call styles for listening tests (never sampled from Square Enix audio).

The v2 calls in synth_wark.py are parrot squawks: bright, trilled, raspy. The FF7
cry is closer to a cartoon "wark": a voiced, vowel-shaped call that rises fast,
holds briefly and drops, with a rounded "w" on the front and a clipped "k" on the
end, and the PS1-era grain on top. Since nobody here can listen, this writes four
styles side by side so Ahmi can pick one (or say "between A and D"), and whatever
he picks becomes the shipped set via `--ship <style>`.

    python tools/wark_candidates.py            # art/sound_candidates/<style>_<call>.wav + .ogg + README
    python tools/wark_candidates.py --ship A   # write that style over sounds/entity/chocobo/*.ogg

Styles
  A  "wark" vowel call: glottal source through w-a-r-k formant tracks, mild vibrato
  B  same as A, higher and brighter ("kweh"-leaning), faster
  C  A with PS1 grain: 11 kHz band-limit, 8-bit quantise, short slap echo
  D  A/B blend with a two-note contour (wa-ARK), the most "cartoon bird"
  E  chip-synth chirp to Ahmi's spec: 80/20 triangle/square oscillator, C6 peak,
     pitch bend -500 c -> 0 in 40 ms then a dive to -700 c on the tail, amp
     attack 0 / decay ~90 ms / sustain 0 / release 10 ms, ~0.3-0.4 s; "wark"
     (wild/alarmed) is the same voice a fourth lower, rougher and longer.
  F  E's pitch contour sung by a bird: additive syrinx voice with fading overtones,
     fast shallow warble + pitch jitter, breath noise riding the pitch, beak
     resonance, an onset puff and a little room. (Ahmi: E "too fake", contour right.)
  G  three-syllable figure (kwe-kwe-KWEEH) set by hand to the reference's notes,
     timing, brightness and held-note tremolo; synthesised, no reference audio
     or pitch data used. Shipped.
"""
from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

import numpy as np
from scipy import signal

sys.path.insert(0, str(Path(__file__).resolve().parent))
from synth_wark import RATE, SND, env_adsr, formant_filter, harmonic_voice, lerp_track, pulse_train, write_wav  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "art/sound_candidates"


def vowel_call(duration, f0_points, *, f1, f2, f3, vib_hz=22.0, vib_depth=0.04, breath=0.012, k_click=0.6,
               w_onset=0.05, attack=0.01, release=0.08, bright=1.0, seed=1):
    """Voiced call: pulse source -> three moving formants. f1..f3 are point lists
    like f0_points, so the vowel can slide w -> a -> r inside one call."""
    rng = np.random.default_rng(seed)
    n = int(RATE * duration)
    t = np.arange(n) / RATE
    f0 = lerp_track(n, f0_points)
    f0 = f0 * (1.0 + vib_depth * np.sin(2 * np.pi * vib_hz * t + 0.5))
    f0 = f0 * (1.0 + 0.006 * rng.standard_normal(n).cumsum() / np.sqrt(np.arange(1, n + 1)))
    src = pulse_train(f0, open_q=0.45)
    src += breath * rng.standard_normal(n)
    tracks = [lerp_track(n, f1), lerp_track(n, f2), lerp_track(n, f3)]
    voice = formant_filter(src, tracks, bw=(110.0, 160.0, 260.0))
    # round the pulse source off (it is +6 dB/oct bright) and keep the hiss out of the top
    b, a = signal.butter(1, 1800 / (RATE / 2))
    voice = 0.35 * voice + 0.65 * signal.lfilter(b, a, voice)
    b, a = signal.butter(4, 5500 / (RATE / 2))
    voice = signal.lfilter(b, a, voice)
    # "w" onset: the first w_onset seconds are darker (lips closing) -> low-pass ramp
    if w_onset > 0:
        b, a = signal.butter(2, 900 / (RATE / 2))
        dark = signal.lfilter(b, a, voice)
        ramp = np.clip(t / w_onset, 0, 1) ** 1.5
        voice = dark * (1 - ramp) + voice * ramp
    # brightness tilt
    b, a = signal.butter(1, 2500 / (RATE / 2), btype="high")
    voice = voice + (bright - 1.0) * 0.6 * signal.lfilter(b, a, voice)
    voice = voice / (np.max(np.abs(voice)) + 1e-9)
    out = voice * env_adsr(n, attack, 0.04, 0.9, release)
    # "k" release: short noise burst at the very end, band-passed
    nc = int(RATE * 0.02)
    burst = rng.standard_normal(nc) * np.linspace(1, 0, nc) ** 1.5
    b, a = signal.butter(2, [1500 / (RATE / 2), 5000 / (RATE / 2)], btype="band")
    burst = signal.lfilter(b, a, burst)
    out[-nc:] += k_click * 0.4 * burst / (np.max(np.abs(burst)) + 1e-9)
    out = np.tanh(1.5 * out) * 0.9
    return np.clip(out, -1, 1)


def ps1_grain(x, rate_hz=11025, bits=8, echo=0.12):
    """PS1-era treatment: band-limit + resample to 11 kHz, 8-bit quantise, small slap echo."""
    step = RATE // rate_hz
    low = signal.resample_poly(x, 1, step)           # band-limited down to ~11 kHz
    q = 2 ** (bits - 1)
    low = np.round(low * q) / q                       # 8-bit quantisation noise
    held = signal.resample_poly(low, step, 1)[:len(x)]
    if len(held) < len(x):
        held = np.pad(held, (0, len(x) - len(held)))
    d = int(RATE * 0.045)
    out = held.copy()
    out[d:] += echo * held[:-d]
    return np.clip(out, -1, 1)


def two(a, b, gap=0.03):
    return np.concatenate([a, np.zeros(int(RATE * gap)), b])


# formant tracks (time, Hz): "w" (rounded, low) -> "a" (open) -> "r" (F3 dips) -> "k"
def wark_formants(d):
    f1 = [(0, 320), (0.06 * d, 780), (0.55 * d, 760), (0.85 * d, 520), (d, 420)]
    f2 = [(0, 800), (0.06 * d, 1350), (0.55 * d, 1300), (0.85 * d, 1500), (d, 1700)]
    f3 = [(0, 2300), (0.06 * d, 2700), (0.55 * d, 2600), (0.80 * d, 1900), (d, 2200)]
    return dict(f1=f1, f2=f2, f3=f3)


def kweh_formants(d):
    f1 = [(0, 300), (0.05 * d, 620), (0.5 * d, 560), (d, 380)]
    f2 = [(0, 1500), (0.05 * d, 2100), (0.5 * d, 2200), (d, 2000)]
    f3 = [(0, 2600), (0.05 * d, 3000), (0.5 * d, 3100), (d, 2900)]
    return dict(f1=f1, f2=f2, f3=f3)


def style_a():
    wark = vowel_call(0.46, [(0, 420), (0.05, 760), (0.14, 880), (0.30, 820), (0.40, 600), (0.46, 380)],
                      **wark_formants(0.46), vib_hz=24, vib_depth=0.05, seed=21)
    kweh = vowel_call(0.38, [(0, 520), (0.04, 980), (0.12, 1120), (0.26, 1040), (0.38, 640)],
                      **kweh_formants(0.38), vib_hz=28, vib_depth=0.04, bright=1.3, w_onset=0.02, seed=22)
    follow = two(vowel_call(0.20, [(0, 600), (0.04, 1050), (0.20, 800)], **kweh_formants(0.20), w_onset=0.015, seed=23),
                 vowel_call(0.24, [(0, 700), (0.04, 1180), (0.24, 860)], **kweh_formants(0.24), w_onset=0.015, seed=24))
    stay = vowel_call(0.55, [(0, 380), (0.10, 640), (0.35, 620), (0.55, 360)], **wark_formants(0.55), vib_hz=18,
                      vib_depth=0.05, k_click=0.3, attack=0.03, release=0.16, w_onset=0.08, seed=25)
    wander = vowel_call(0.17, [(0, 700), (0.03, 1150), (0.17, 850)], **kweh_formants(0.17), w_onset=0.01,
                        k_click=0.3, breath=0.02, seed=26) * 0.7
    return {"wark": wark, "kweh": kweh, "kweh_follow": follow, "kweh_stay": stay, "kweh_wander": wander}


def style_b():
    a = style_a()
    out = {}
    for k, v in a.items():
        # pitch up ~4 semitones and 12% shorter: resample
        n = int(len(v) / 1.26)
        y = signal.resample(v, n)
        b, bb = signal.butter(1, 1800 / (RATE / 2), btype="high")
        y = y + 0.35 * signal.lfilter(b, bb, y)
        out[k] = np.clip(y / (np.max(np.abs(y)) + 1e-9) * 0.9, -1, 1)
    return out


def style_c():
    return {k: ps1_grain(v) for k, v in style_a().items()}


def style_d():
    """Two-note contour: a short low 'wa' then the 'ARK' jumping a fifth and dropping."""
    wark = two(vowel_call(0.13, [(0, 380), (0.05, 520), (0.13, 500)], **wark_formants(0.13), k_click=0.0,
                          release=0.03, seed=31),
               vowel_call(0.34, [(0, 760), (0.05, 900), (0.20, 840), (0.34, 420)], **wark_formants(0.34),
                          w_onset=0.0, vib_hz=26, vib_depth=0.06, seed=32), gap=0.012)
    kweh = two(vowel_call(0.10, [(0, 520), (0.04, 700), (0.10, 690)], **kweh_formants(0.10), k_click=0.0,
                          release=0.03, seed=33),
               vowel_call(0.30, [(0, 1000), (0.05, 1160), (0.18, 1080), (0.30, 620)], **kweh_formants(0.30),
                          w_onset=0.0, bright=1.3, vib_hz=30, vib_depth=0.05, seed=34), gap=0.012)
    a = style_a()
    return {"wark": wark, "kweh": kweh, "kweh_follow": a["kweh_follow"], "kweh_stay": a["kweh_stay"],
            "kweh_wander": a["kweh_wander"]}


def chip(duration, peak_hz, *, bend=(-500.0, 0.0, 40.0), dive=(-700.0, 0.12), decay=0.09, release=0.01,
         square=0.20, crunch=10, vib=(0.0, 0.0), seed=1):
    """Triangle/square hybrid oscillator with a cents pitch envelope.
    bend = (start_cents, end_cents, ms): the "kw" snap up at the onset.
    dive = (cents, seconds): the "eh" drop over the last `seconds`.
    crunch = bit depth for the retro grain (None = clean)."""
    n = int(RATE * duration)
    t = np.arange(n) / RATE
    cents = np.full(n, bend[1])
    up = t < bend[2] / 1000.0
    cents[up] = bend[0] + (bend[1] - bend[0]) * (t[up] / (bend[2] / 1000.0))
    tail = t > duration - dive[1]
    cents[tail] += dive[0] * ((t[tail] - (duration - dive[1])) / dive[1]) ** 1.6
    f0 = peak_hz * 2.0 ** (cents / 1200.0)
    if vib[0] > 0:
        f0 = f0 * (1.0 + vib[1] * np.sin(2 * np.pi * vib[0] * t))
    phase = (np.cumsum(f0 / RATE)) % 1.0
    tri = 4.0 * np.abs(phase - 0.5) - 1.0
    sq = np.where(phase < 0.5, 1.0, -1.0)
    osc = (1.0 - square) * tri + square * sq
    # amp envelope: instant attack, fast decay to a low floor, hard release
    env = np.ones(n)
    dec = t < decay
    env[dec] = 1.0 - 0.75 * (t[dec] / decay)
    env[~dec] = 0.25
    rel = t > duration - release
    env[rel] *= np.clip((duration - t[rel]) / release, 0, 1)
    env[:int(RATE * 0.001)] *= np.linspace(0, 1, int(RATE * 0.001))  # no DC click
    out = osc * env
    b, a = signal.butter(2, 6500 / (RATE / 2))
    out = signal.lfilter(b, a, out)
    if crunch:
        q = 2 ** (crunch - 1)
        out = np.round(out * q) / q
    return np.clip(out / (np.max(np.abs(out)) + 1e-9) * 0.9, -1, 1)


C6, G5, E5, D5, A5 = 1046.5, 784.0, 659.3, 587.3, 880.0


def style_e():
    kweh = chip(0.36, C6)                                              # spec: G5 snap -> C6 -> dive
    wark = chip(0.48, G5, bend=(-400.0, 0.0, 55.0), dive=(-900.0, 0.18), decay=0.14, square=0.45,
                crunch=8, vib=(28.0, 0.03))                             # wild/alarmed: lower, rougher, longer
    follow = two(chip(0.20, C6, dive=(-500.0, 0.06), decay=0.07), chip(0.24, C6 * 2 ** (2 / 12), decay=0.08), gap=0.05)
    stay = chip(0.42, G5, bend=(-300.0, 0.0, 60.0), dive=(-600.0, 0.16), decay=0.16, square=0.10, crunch=None)
    wander = chip(0.22, A5, bend=(-500.0, 0.0, 30.0), dive=(-700.0, 0.08), decay=0.06) * 0.7
    return {"wark": wark, "kweh": kweh, "kweh_follow": follow, "kweh_stay": stay, "kweh_wander": wander}


def bird(duration, peak_hz, *, bend=(-500.0, 0.0, 40.0), dive=(-700.0, 0.12), decay=0.11, release=0.03,
         trill=(46.0, 0.035), jitter=0.012, breath=0.10, chuff=0.35, amps=(1.0, 0.55, 0.30, 0.16, 0.08, 0.04),
         body=(2400.0, 0.35), room=0.10, seed=1):
    """Style E's pitch contour sung by a bird instead of a chip: a syrinx-style
    additive voice (few decaying overtones), a shallow fast warble, random pitch
    jitter, breath noise following the pitch, a beak/skull resonance, a puff of
    air on the onset and a touch of room. The contour parameters mean the same
    as in chip()."""
    rng = np.random.default_rng(seed)
    n = int(RATE * duration)
    t = np.arange(n) / RATE
    cents = np.full(n, bend[1])
    up = t < bend[2] / 1000.0
    cents[up] = bend[0] + (bend[1] - bend[0]) * (t[up] / (bend[2] / 1000.0)) ** 0.8
    tail = t > duration - dive[1]
    cents[tail] += dive[0] * ((t[tail] - (duration - dive[1])) / dive[1]) ** 1.6
    f0 = peak_hz * 2.0 ** (cents / 1200.0)
    # warble + random-walk jitter: a real syrinx is never dead steady
    f0 = f0 * (1.0 + trill[1] * np.sin(2 * np.pi * trill[0] * t + 1.1))
    walk = rng.standard_normal(n).cumsum()
    walk = walk / (np.abs(walk).max() + 1e-9)
    f0 = f0 * (1.0 + jitter * walk)
    voice = harmonic_voice(f0, amps)
    # overtones fade as the call decays (the bird closes down): tilt darker over time
    b, a = signal.butter(1, 2200 / (RATE / 2))
    dark = signal.lfilter(b, a, voice)
    mix = np.clip(t / duration, 0, 1) ** 1.5
    voice = voice * (1 - 0.6 * mix) + dark * 0.6 * mix
    # breath: noise band-passed around the moving fundamental, louder at onset and tail
    noise = rng.standard_normal(n)
    frames = 512
    br = np.zeros(n)
    zi = None
    for s0 in range(0, n, frames):
        e0 = min(n, s0 + frames)
        fc = float(np.clip(np.mean(f0[s0:e0]) * 1.6, 300, 8000))
        bb, ab = signal.butter(2, [max(fc * 0.55, 100) / (RATE / 2), min(fc * 1.8, 12000) / (RATE / 2)], btype="band")
        if zi is None:
            zi = signal.lfilter_zi(bb, ab) * 0.0
        seg, zi = signal.lfilter(bb, ab, noise[s0:e0], zi=zi)
        br[s0:e0] = seg
    br = br / (np.max(np.abs(br)) + 1e-9)
    bshape = 0.5 + 0.5 * np.abs(np.cos(np.pi * t / duration)) ** 2  # more air at the ends
    voice = voice + breath * br * bshape
    # beak / skull resonance
    fc, m = body
    bp, ap = signal.iirpeak(fc, fc / 420.0, fs=RATE)
    voice = (1 - m) * voice + m * signal.lfilter(bp, ap, voice)
    voice = voice / (np.max(np.abs(voice)) + 1e-9)
    # natural envelope: 4 ms attack, exponential-ish decay to a body, quick release
    env = np.exp(-t / decay) * 0.8 + 0.2
    env *= np.clip(t / 0.004, 0, 1)
    rel = t > duration - release
    env[rel] *= np.clip((duration - t[rel]) / release, 0, 1) ** 0.7
    out = voice * env
    # onset puff of air ("k")
    nc = int(RATE * 0.014)
    puff = rng.standard_normal(nc) * np.linspace(1, 0, nc) ** 2
    bq, aq = signal.butter(2, [2000 / (RATE / 2), 7000 / (RATE / 2)], btype="band")
    puff = signal.lfilter(bq, aq, puff)
    out[:nc] += chuff * 0.5 * puff / (np.max(np.abs(puff)) + 1e-9)
    # a little room: two short reflections
    if room > 0:
        wet = np.zeros(n + int(RATE * 0.09))
        wet[:n] += out
        wet[int(RATE * 0.031):int(RATE * 0.031) + n] += room * out
        wet[int(RATE * 0.067):int(RATE * 0.067) + n] += room * 0.6 * out
        bl, al = signal.butter(1, 4000 / (RATE / 2))
        out = wet[:n] * (1 - room) + signal.lfilter(bl, al, wet)[:n] * room
    out = np.tanh(1.3 * out / (np.max(np.abs(out)) + 1e-9)) * 0.9
    return np.clip(out, -1, 1)


def style_f():
    """E's contour, bird voice."""
    kweh = bird(0.36, C6, seed=41)
    wark = bird(0.48, G5, bend=(-400.0, 0.0, 55.0), dive=(-900.0, 0.18), decay=0.16, trill=(38.0, 0.05),
                jitter=0.02, breath=0.16, amps=(1.0, 0.8, 0.55, 0.35, 0.2, 0.1, 0.06), body=(1900.0, 0.45), seed=42)
    follow = two(bird(0.20, C6, dive=(-500.0, 0.06), decay=0.08, seed=43),
                 bird(0.24, C6 * 2 ** (2 / 12), decay=0.09, seed=44), gap=0.05)
    stay = bird(0.42, G5, bend=(-300.0, 0.0, 60.0), dive=(-600.0, 0.16), decay=0.2, trill=(30.0, 0.03),
                breath=0.08, chuff=0.15, seed=45)
    wander = bird(0.22, A5, bend=(-500.0, 0.0, 30.0), dive=(-700.0, 0.08), decay=0.07, seed=46) * 0.7
    return {"wark": wark, "kweh": kweh, "kweh_follow": follow, "kweh_stay": stay, "kweh_wander": wander}


def tone(duration, f0_points, amps, env_points, *, trem=(0.0, 0.0, 0.0, 0.0), breath=0.04, jitter=0.006,
         body=(2300.0, 0.25), seed=1):
    """One syllable: additive tone on a hand-set note contour (Hz points), a
    hand-set loudness curve (0..1 points), optional amplitude tremolo
    (hz, depth, start_s, end_s), a little breath and pitch jitter."""
    rng = np.random.default_rng(seed)
    n = int(RATE * duration)
    t = np.arange(n) / RATE
    f0 = lerp_track(n, f0_points)
    walk = rng.standard_normal(n).cumsum()
    f0 = f0 * (1.0 + jitter * walk / (np.abs(walk).max() + 1e-9))
    voice = harmonic_voice(f0, amps)
    noise = rng.standard_normal(n)
    bb, ab = signal.butter(2, [600 / (RATE / 2), 5000 / (RATE / 2)], btype="band")
    voice = voice + breath * signal.lfilter(bb, ab, noise) / 3.0
    fc, m = body
    bp, ap = signal.iirpeak(fc, fc / 400.0, fs=RATE)
    voice = (1 - m) * voice + m * signal.lfilter(bp, ap, voice)
    env = lerp_track(n, env_points)
    hz, depth, t0, t1 = trem
    if hz > 0:
        w = np.clip((t - t0) / 0.02, 0, 1) * np.clip((t1 - t) / 0.02, 0, 1)
        env = env * (1.0 - depth * w * (0.5 + 0.5 * np.sin(2 * np.pi * hz * t)))
    out = voice * env
    return out / (np.max(np.abs(out)) + 1e-9)


def seq(parts, gaps):
    """Concatenate syllables with silent gaps (seconds)."""
    out = []
    for i, p in enumerate(parts):
        out.append(p)
        if i < len(gaps):
            out.append(np.zeros(int(RATE * gaps[i])))
    x = np.concatenate(out)
    return np.clip(x / (np.max(np.abs(x)) + 1e-9) * 0.9, -1, 1)


def style_g():
    """Three-syllable "kwe-kwe-KWEEH" set by hand from what the reference does
    (notes, timing, brightness, the tremolo on the held note) — no audio or
    pitch data from it is used. Syllable 1: short breathy rise A5->B5, hard cut.
    Syllable 2: A#5 up to C6 back to B5, bright (2nd harmonic dominant).
    Syllable 3: C#6 climbing to a held D6 with a ~32 Hz amplitude tremolo,
    then falling through C6 to G5 and fading."""
    s1 = tone(0.075, [(0, 930), (0.03, 985), (0.075, 1010)], (1.0, 0.15, 0.05),
              [(0, 0.05), (0.06, 1.0), (0.068, 0.9), (0.075, 0.05)], breath=0.14, seed=51)
    s2 = tone(0.16, [(0, 910), (0.03, 965), (0.07, 1030), (0.11, 1020), (0.16, 965)], (0.6, 1.0, 0.3, 0.1),
              [(0, 0.1), (0.07, 1.0), (0.10, 0.85), (0.14, 0.3), (0.16, 0.05)], breath=0.05, seed=52)
    s3 = tone(0.34, [(0, 1075), (0.03, 1095), (0.08, 1165), (0.17, 1165), (0.20, 1140), (0.24, 1060), (0.28, 960),
                     (0.31, 860), (0.34, 780)], (1.0, 0.7, 0.2, 0.08),
              [(0, 0.1), (0.02, 0.6), (0.05, 1.0), (0.17, 0.75), (0.24, 0.45), (0.30, 0.2), (0.34, 0.0)],
              trem=(32.0, 0.55, 0.05, 0.20), breath=0.06, seed=53)
    kweh = seq([s1, s2, s3], [0.045, 0.025])
    # wark: same figure a fourth down, slower, rougher (more overtones, deeper tremolo)
    def lower(f): return [(t * 1.15, hz * 0.75) for t, hz in f]
    w1 = tone(0.085, lower([(0, 930), (0.03, 985), (0.075, 1010)]), (1.0, 0.5, 0.25, 0.1),
              [(0, 0.05), (0.07, 1.0), (0.085, 0.05)], breath=0.16, seed=54)
    w2 = tone(0.18, lower([(0, 910), (0.03, 965), (0.07, 1030), (0.11, 1020), (0.16, 965)]), (0.8, 1.0, 0.5, 0.25),
              [(0, 0.1), (0.08, 1.0), (0.16, 0.3), (0.18, 0.05)], breath=0.08, seed=55)
    w3 = tone(0.40, lower([(0, 1075), (0.03, 1095), (0.08, 1165), (0.17, 1165), (0.20, 1140), (0.24, 1060), (0.28, 960),
                           (0.31, 860), (0.34, 780)]), (1.0, 0.9, 0.5, 0.25, 0.1),
              [(0, 0.1), (0.05, 1.0), (0.20, 0.8), (0.30, 0.4), (0.40, 0.0)], trem=(26.0, 0.7, 0.06, 0.24),
              breath=0.09, body=(1800.0, 0.35), seed=56)
    wark = seq([w1, w2, w3], [0.05, 0.03])
    follow = seq([s1, s2, tone(0.16, [(0, 1000), (0.04, 1100), (0.10, 1120), (0.16, 1000)], (0.7, 1.0, 0.3),
                              [(0, 0.1), (0.05, 1.0), (0.13, 0.5), (0.16, 0.0)], seed=57)], [0.045, 0.06])
    stay = seq([s2 * 0.8, tone(0.30, [(0, 1000), (0.06, 1060), (0.16, 1050), (0.30, 820)], (1.0, 0.5, 0.15),
                              [(0, 0.1), (0.06, 1.0), (0.18, 0.6), (0.30, 0.0)], trem=(28.0, 0.35, 0.06, 0.18), seed=58)],
               [0.04])
    wander = seq([s1, s2], [0.045]) * 0.7
    return {"wark": wark, "kweh": kweh, "kweh_follow": follow, "kweh_stay": stay, "kweh_wander": wander}


STYLES = {"A": style_a, "B": style_b, "C": style_c, "D": style_d, "E": style_e, "F": style_f, "G": style_g}


def encode(wav: Path, ogg: Path):
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg:
        subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "5", str(ogg)],
                       check=True)


def main():
    if "--ship" in sys.argv:
        style = sys.argv[sys.argv.index("--ship") + 1].upper()
        for name, data in STYLES[style]().items():
            wav = SND / f"{name}.wav"
            write_wav(wav, data)
            encode(wav, wav.with_suffix(".ogg"))
            wav.unlink()
            print("shipped", style, name)
        return
    OUT.mkdir(parents=True, exist_ok=True)
    for style, fn in STYLES.items():
        for name, data in fn().items():
            wav = OUT / f"{style}_{name}.wav"
            write_wav(wav, data)
            encode(wav, wav.with_suffix(".ogg"))
    (OUT / "README.md").write_text(__doc__.split("Styles")[0] + "Styles" + __doc__.split("Styles")[1])
    print("wrote", OUT)


if __name__ == "__main__":
    main()
