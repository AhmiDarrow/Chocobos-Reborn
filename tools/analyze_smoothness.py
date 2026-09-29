"""Smoothness report for a harness results folder: how the other racers moved on screen, frame pacing, snap-backs.

python tools/analyze_smoothness.py build/latency/results [--json out.json]

Reads, per client name:
  motion-<name>.csv  track,tick,entity,x,y,z  every client tick, every other racing bird in view
  frames-<name>.csv  track,tick,frames,p50_ms,p99_ms,max_ms,over33,delay_ticks,bird_cpu_ms_per_frame,birds_per_frame
  client*.csv        column 11 vanilla vehicle corrections, column 18 (new builds) acknowledged teleports
and any server log it finds for "move refused" / "moved wrongly" / "moved too quickly".

Motion: the game draws a bird between its last two tick positions, so a bird's per-tick step is exactly
what the rider saw. For a racer the step changes gradually (speed, turns). "Judder" is how much a step
differs from the average of its neighbours, relative to the bird's speed: a steady bird scores ~0, a bird
that stalls one tick and leaps the next scores ~1. Stalls are ticks a moving bird did not move; leaps are
steps over twice the neighbouring average. Teleports (steps over 16 blocks) are excluded.
"""
import argparse
import csv
import glob
import json
import math
import os
import re
import statistics
from collections import defaultdict


def pct(values, q):
    if not values:
        return float("nan")
    s = sorted(values)
    return s[min(len(s) - 1, int(len(s) * q))]


def motion_report(path):
    by_bird = defaultdict(list)
    for row in csv.reader(open(path, encoding="utf-8")):
        if len(row) < 6:
            continue
        try:
            by_bird[(row[0], int(row[2]))].append((int(row[1]), float(row[3]), float(row[4]), float(row[5])))
        except ValueError:
            continue
    judder, stalls, leaps, samples = [], 0, 0, 0
    for rows in by_bird.values():
        rows.sort()
        steps = []
        for (t0, x0, y0, z0), (t1, x1, y1, z1) in zip(rows, rows[1:]):
            if t1 - t0 != 1:
                steps.append(None)
                continue
            d = math.dist((x0, y0, z0), (x1, y1, z1))
            steps.append(None if d > 16 else d)
        for i in range(1, len(steps) - 1):
            a, b, c = steps[i - 1], steps[i], steps[i + 1]
            if a is None or b is None or c is None:
                continue
            around = (a + c) / 2
            if around < 0.15:  # standing on the grid, or nearly
                continue
            samples += 1
            judder.append(abs(b - around) / around)
            if b < 0.02 * around + 1e-4:
                stalls += 1
            if b > 2 * around:
                leaps += 1
    return {
        "birds": len(by_bird),
        "samples": samples,
        "judder_p50": round(pct(judder, 0.5), 4),
        "judder_p99": round(pct(judder, 0.99), 4),
        "judder_max": round(max(judder), 4) if judder else None,
        "stalls_per_1000": round(1000 * stalls / samples, 2) if samples else None,
        "leaps_per_1000": round(1000 * leaps / samples, 2) if samples else None,
    }


def frames_report(path):
    p50, p99, worst, over33, frames, delay, bird_ms, birds = [], [], 0.0, 0, 0, [], [], []
    for row in csv.reader(open(path, encoding="utf-8")):
        if len(row) < 7:
            continue
        try:
            n = int(row[2])
            frames += n
            p50.append(float(row[3]))
            p99.append(float(row[4]))
            worst = max(worst, float(row[5]))
            over33 += int(row[6])
            if len(row) > 7:
                delay.append(float(row[7]))
            if len(row) > 9:
                bird_ms.append(float(row[8]))
                birds.append(float(row[9]))
        except ValueError:
            continue
    return {
        "frames": frames,
        "frame_ms_p50": round(statistics.median(p50), 2) if p50 else None,
        "frame_ms_p99_median_window": round(statistics.median(p99), 2) if p99 else None,
        "frame_ms_p99_worst_window": round(max(p99), 2) if p99 else None,
        "frame_ms_max": round(worst, 2),
        "hitches_over_33ms": over33,
        "playout_delay_ticks_median": round(statistics.median(delay), 2) if delay else None,
        "bird_render_cpu_ms_per_frame_median": round(statistics.median(bird_ms), 3) if bird_ms else None,
        "bird_render_cpu_ms_per_frame_max_window": round(max(bird_ms), 3) if bird_ms else None,
        "birds_drawn_per_frame_median": round(statistics.median(birds), 1) if birds else None,
    }


def client_report(path):
    corrections, teleports = 0, 0
    for row in csv.reader(open(path, encoding="utf-8")):
        try:
            corrections = max(corrections, int(row[10]))
            if len(row) > 17:
                teleports = max(teleports, int(row[17]))
        except (ValueError, IndexError):
            continue
    return {"vanilla_corrections": corrections, "acknowledged_teleports": teleports}


def crowd_report(folder):
    """Server tick times of a crowded heat, and each simulated player's link and what it went through."""
    ticks, riders = [], []
    for path in glob.glob(os.path.join(folder, "**", "crowd-server.csv"), recursive=True):
        for row in csv.reader(open(path, encoding="utf-8")):
            try:
                ticks.append(float(row[2]))
            except (ValueError, IndexError):
                continue
    for path in glob.glob(os.path.join(folder, "**", "crowd-riders.csv"), recursive=True):
        for row in csv.reader(open(path, encoding="utf-8")):
            if len(row) >= 9:
                riders.append({"name": row[0], "rtt_ms": row[1], "jitter_ms": row[2], "client_ticks": row[3],
                               "teleports": row[4], "vanilla_corrections": row[5], "player_teleports": row[6],
                               "frames": row[7], "playout_delay_ticks": row[8]})
    if not ticks and not riders:
        return None
    return {
        "server_ticks": len(ticks),
        "tick_ms_p50": round(pct(ticks, 0.5), 2) if ticks else None,
        "tick_ms_p99": round(pct(ticks, 0.99), 2) if ticks else None,
        "tick_ms_max": round(max(ticks), 2) if ticks else None,
        "ticks_over_50ms": sum(1 for t in ticks if t > 50),
        "simulated_players": riders,
    }


def server_logs(folder):
    counts = {"move_refused": 0, "moved_wrongly": 0, "moved_too_quickly": 0}
    for log in glob.glob(os.path.join(folder, "**", "*.log"), recursive=True):
        try:
            text = open(log, encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        counts["move_refused"] += len(re.findall(r"move refused #", text))
        counts["moved_wrongly"] += len(re.findall(r"moved wrongly!", text))
        counts["moved_too_quickly"] += len(re.findall(r"moved too quickly!", text))
    return counts


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("folder")
    ap.add_argument("--json", default="")
    args = ap.parse_args()
    report = {"clients": {}, "server": server_logs(args.folder)}
    crowd = crowd_report(args.folder)
    if crowd:
        report["crowd"] = crowd
    for path in glob.glob(os.path.join(args.folder, "**", "motion-*.csv"), recursive=True):
        name = os.path.basename(path)[len("motion-"):-4]
        report["clients"].setdefault(name, {})["motion"] = motion_report(path)
    for path in glob.glob(os.path.join(args.folder, "**", "frames-*.csv"), recursive=True):
        name = os.path.basename(path)[len("frames-"):-4]
        report["clients"].setdefault(name, {})["frames"] = frames_report(path)
    for path in glob.glob(os.path.join(args.folder, "**", "client*.csv"), recursive=True):
        base = os.path.basename(path)
        name = "LatencyRider" if base == "client.csv" else base[len("client-"):-4]
        report["clients"].setdefault(name, {})["snaps"] = client_report(path)
    text = json.dumps(report, indent=2)
    print(text)
    if args.json:
        open(args.json, "w", encoding="utf-8").write(text)


if __name__ == "__main__":
    main()
