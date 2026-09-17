"""Chocobo pose clips for the NCGB exporter (idle / walk / run).

Ratite gait (ostrich / FF7 race bird): the visible backward joint is the
ankle. Femur (leg_*) swings fore-aft, tarsus (shin_*) folds on the airborne
pass and extends to plant, toes (foot_*) stay flat on the ground.

Run is a two-beat bound with a suspension phase: long stride, body leans
forward, neck stretched out and low with a counter-bob so the eyes stay
level, tail lifted as a counterweight, wings held slightly out.

All angles are radians on XYZ Euler; X = pitch about the bone's own axis.
`loc` values are metres in bone space (scaled from the 2.25 m adult).
"""
from __future__ import annotations

import math

TAU = math.tau
H = 2.25  # adult height; loc offsets scale with this


def _smooth_pulse(phase, width):
    """1 at phase 0, cosine falloff to 0 by |phase| >= width. phase in [-0.5, 0.5)."""
    p = (phase + 0.5) % 1.0 - 0.5
    if abs(p) >= width:
        return 0.0
    return 0.5 + 0.5 * math.cos(math.pi * p / width)


def gait(t, *, thigh, tuck, crouch, hop, lean, tail, wing, neck_drop, stride_phase=0.0,
         air_width=0.30, bob_scale=1.0):
    """t in [0,1): one full stride (left then right)."""
    tl = t
    tr = (t + 0.5) % 1.0
    # femur swing: forward at plant, back at push-off
    leg_l = 0.08 + thigh * math.sin(tl * TAU)
    leg_r = 0.08 + thigh * math.sin(tr * TAU)
    # airborne pulse: peaks mid-swing (t=0.5 of that leg's cycle), width sets stance share
    air_l = _smooth_pulse(tl - 0.5, air_width)
    air_r = _smooth_pulse(tr - 0.5, air_width)
    # tarsus: folds during swing, small extension at touchdown (t≈0.1) and push (t≈0.9)
    ext_l = 0.18 * math.sin((tl - 0.02) * TAU) * (1.0 - air_l)
    ext_r = 0.18 * math.sin((tr - 0.02) * TAU) * (1.0 - air_r)
    # bird ankle: standing = tarsus angled a little forward (-crouch); in the
    # air it folds BACK so the foot tucks up behind (+tuck), then extends to plant.
    shin_l = -crouch + tuck * air_l + ext_l
    shin_r = -crouch + tuck * air_r + ext_r
    # toes: the foot bone runs forward, so its pitch sign is opposite to the
    # leg chain; +(leg+shin) keeps the plate flat on the ground during stance,
    # toes drop a little in the air.
    foot_l = (leg_l + shin_l) * (1.0 - air_l) - 0.20 * air_l
    foot_r = (leg_r + shin_r) * (1.0 - air_r) - 0.20 * air_r
    # body bob twice per stride, peak at suspension (mid-air of either leg)
    bob = math.sin(tl * TAU * 2.0 - 0.5 * math.pi) * bob_scale
    sway = math.sin(tl * TAU)
    tswing = math.sin((tl - 0.10) * TAU)
    return {
        "rot": {
            "body": (lean + 0.025 * bob, 0.010 * sway, 0.025 * sway),
            "neck": (-neck_drop - 0.5 * lean - 0.030 * bob, 0.012 * sway, 0.0),
            "neck_mid": (-0.35 * neck_drop - 0.25 * lean - 0.020 * bob, 0.0, 0.0),
            "head": (1.35 * neck_drop + 0.75 * lean + 0.045 * bob, 0.010 * sway, 0.0),
            "crest": (0.06 * bob, 0.0, 0.03 * sway),
            "crest_male": (0.05 * bob, 0.0, 0.02 * sway),
            "tail": (tail + 0.12 * tswing, 0.02 * bob, 0.30 * tail * sway),
            "wing_l": (0.03 * bob, 0.04 * air_l, wing + 0.05 * air_l + 0.03 * bob),
            "wing_r": (0.03 * bob, -0.04 * air_r, -wing - 0.05 * air_r - 0.03 * bob),
            "leg_l": (leg_l, 0.0, 0.0),
            "shin_l": (shin_l, 0.0, 0.0),
            "foot_l": (foot_l, 0.0, 0.0),
            "leg_r": (leg_r, 0.0, 0.0),
            "shin_r": (shin_r, 0.0, 0.0),
            "foot_r": (foot_r, 0.0, 0.0),
        },
        "loc": {
            "root": (0.0, 0.0, hop * H * max(0.0, bob)),
            "body": (0.0, 0.004 * H * sway, 0.40 * hop * H * bob),
        },
    }


def walk_pose(t):
    """Pad-runner walk: high bird step, ankle tucks the foot up, head nods."""
    return gait(t, thigh=0.34, tuck=0.42, crouch=0.14, hop=0.010, lean=0.04, tail=0.10,
                wing=0.04, neck_drop=0.05, air_width=0.24, bob_scale=0.8)


def run_pose(t):
    """Race sprint: long stride, deep tuck, suspension hop, neck out, tail up."""
    return gait(t, thigh=0.62, tuck=0.95, crouch=0.10, hop=0.022, lean=0.17, tail=0.26,
                wing=0.22, neck_drop=0.28, air_width=0.34, bob_scale=1.0)


def idle_pose(t):
    """Standing bird: breath, look-around, tail pendulum, wing fluff, ankle crouch."""
    s = math.sin(t * TAU)
    c = math.cos(t * TAU)
    s2 = math.sin(t * TAU * 2)
    s3 = math.sin(t * TAU * 3)
    look = math.sin(t * TAU * 0.5 + 0.4)
    breath = 0.5 + 0.5 * math.sin(t * TAU * 2.0)
    return {
        "rot": {
            "body": (0.030 * s, 0.010 * s2, 0.015 * c),
            "neck": (0.050 * math.sin(t * TAU + 0.55), 0.04 * c + 0.05 * look, 0.012 * s),
            "neck_mid": (0.035 * math.sin(t * TAU + 0.9), 0.035 * look, 0.01 * c),
            "head": (-0.040 * s + 0.020 * s2, 0.04 * math.sin(t * TAU + 1.2) + 0.06 * look, 0.02 * s3),
            "crest": (0.08 * s2, 0.03 * s, 0.05 * c),
            "crest_male": (0.06 * s2, 0.02 * c, 0.04 * s),
            "tail": (0.12 * s + 0.04 * s2, 0.03 * c, 0.10 * c),
            "wing_l": (0.04 * s + 0.02 * breath, 0.02 * c, 0.07 * c + 0.03 * s2),
            "wing_r": (0.04 * s + 0.02 * breath, -0.02 * c, -0.07 * c - 0.03 * s2),
            "leg_l": (0.06 + 0.02 * s, 0.0, 0.0),
            "leg_r": (0.06 - 0.02 * s, 0.0, 0.0),
            "shin_l": (-0.10 - 0.02 * s2, 0.0, 0.0),
            "shin_r": (-0.10 - 0.02 * s2, 0.0, 0.0),
            "foot_l": (-0.04 - 0.02 * s2, 0.0, 0.0),
            "foot_r": (-0.04 + 0.02 * s2, 0.0, 0.0),
        },
        "loc": {
            "root": (0.0, 0.0, 0.004 * H * s2),
            "body": (0.003 * H * c, 0.004 * H * s, 0.010 * H * breath),
            "head": (0.002 * H * look, 0.004 * H * c, 0.002 * H * s),
            "tail": (0.005 * H * c, 0.004 * H * s, 0.004 * H * s2),
        },
    }
