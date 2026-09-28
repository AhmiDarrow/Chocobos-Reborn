"""Summarize both sides of real integrated-host/remote-guest races; nonzero exit on incomplete coverage."""
import csv
import json
import math
import re
import statistics
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build/latency-pair/results"
events = [json.loads(line) for line in (out / "events.jsonl").read_text().splitlines()]
ends = [e for e in events if e.get("event") == "end"]
clients = {}
client_ends = {}
for role in ("LatencyHost", "LatencyGuest", "LatencyGuest2"):
    path = out / ("client-" + role + ".csv")
    clients[role] = list(csv.reader(path.open())) if path.exists() else []
    end_path = out / ("client-end-" + role + ".jsonl")
    client_ends[role] = [json.loads(line) for line in end_path.read_text().splitlines()] if end_path.exists() else []
# the bot's wall recoveries (RaceHarnessClient): run, tick, track, x, y, z, progress, lane, yaw
recoveries = {}
for role in ("LatencyHost", "LatencyGuest", "LatencyGuest2"):
    path = out / ("recover-" + role + ".csv")
    recoveries[role] = list(csv.reader(path.open())) if path.exists() else []
field_path = out / "field.jsonl"
field_samples = [json.loads(line) for line in field_path.read_text().splitlines()] if field_path.exists() else []
summary = []
for event in ends:
    host, guest = event["host"], event["guest"]
    participants = [event[key] for key in ("host", "guest", "guest2") if key in event]
    finished = (all(p["place"] > 0 and not p["forfeited"] for p in participants)
                and all(isinstance(p.get(k), (int, float)) and math.isfinite(p[k])
                        for p in participants for k in ("observedTick", "creditedTick"))
                and not event["stuck"] and not event["timeout"])
    row = {"track": event["track"], "both_finished": finished, "ticks": event["ticks"],
           "host_place": host["place"], "guest_place": guest["place"],
           "host_rtt_start_finish": [host["startLatencyMs"], host["finishLatencyMs"]],
           "guest_rtt_start_finish": [guest["startLatencyMs"], guest["finishLatencyMs"]]}
    if finished:
        row["guest_minus_host_observed_ms"] = round((guest["observedTick"] - host["observedTick"]) * 50, 1)
        row["guest_minus_host_credited_ms"] = round((guest["creditedTick"] - host["creditedTick"]) * 50, 1)
    row["all_humans_finished"] = finished
    row["field_size"] = event.get("field", 2)
    row["field_finished"] = event.get("finished")
    if "guest2" in event:
        third = event["guest2"]
        row["guest2_place"] = third["place"]
        row["guest2_rtt_start_finish"] = [third["startLatencyMs"], third["finishLatencyMs"]]
        if finished:
            row["guest2_minus_host_credited_ms"] = round((third["creditedTick"] - host["creditedTick"]) * 50, 1)
    row["telemetry_complete"] = True
    paired_samples = {}
    for role, label in (("LatencyHost", "host"), ("LatencyGuest", "guest"), ("LatencyGuest2", "guest2")):
        if label not in event:
            continue
        samples = [r for r in clients[role] if r[9] == event["track"]]
        end_samples = [r for r in client_ends[role] if r["track"] == event["track"]]
        row["telemetry_complete"] &= bool(samples and end_samples)
        row[label + "_vehicle_corrections"] = max([int(r[10]) for r in samples] + [r["vehicle_corrections"] for r in end_samples] + [0])
        row[label + "_correction_count_source"] = "end_event" if end_samples else "sampled"
        row[label + "_last_client_tick"] = int(samples[-1][1]) if samples else None
        performance_path = out / ("performance-" + role + ".csv")
        if performance_path.exists():
            perf = [r for r in csv.reader(performance_path.open()) if r[0] == event["track"]]
            fps = sorted(int(r[2]) for r in perf)
            intervals = sorted(float(r[3]) for r in perf if float(r[3]) > 0)
            if fps:
                row[label + "_sampled_fps_median"] = statistics.median(fps)
            if intervals:
                row[label + "_ten_tick_interval_ms_p95"] = round(intervals[min(len(intervals) - 1, int(len(intervals) * .95))], 2)
        paired_samples[label] = {int(r[1]): r for r in samples}
        row[label + "_collision_samples"] = sum(len(r) > 13 and r[13] == "true" for r in samples)
        row[label + "_peak_sampled_speed"] = round(max((float(r[11]) for r in samples if len(r) > 11), default=0), 4)
        stuck = [r for r in recoveries[role] if len(r) > 2 and r[2] == event["track"]]
        row[label + "_wall_recoveries"] = len(stuck)
        if stuck:
            row[label + "_wall_recovery_progress"] = [round(float(r[6]), 4) for r in stuck][:12]
    for label in ("guest", "guest2"):
        if label not in paired_samples:
            continue
        common_ticks = paired_samples["host"].keys() & paired_samples[label].keys()
        pairs = [(paired_samples["host"][t], paired_samples[label][t]) for t in common_ticks]
        distances = [math.hypot(float(h[2]) - float(g[2]), float(h[4]) - float(g[4])) for h, g in pairs]
        prefix = label + "_same_running_tick_"
        row[prefix + "path_gap_median_blocks"] = round(statistics.median(distances), 3) if distances else None
        row[prefix + "path_gap_max_blocks"] = round(max(distances), 3) if distances else None
        row[prefix + "stamina_difference_max"] = max((abs(int(h[6]) - int(g[6])) for h, g in pairs), default=None)
        row[prefix + "dash_lock_mismatch_samples"] = sum(h[7] != g[7] for h, g in pairs)
        row[prefix + "sample_count"] = len(pairs)
    row["review_needed"] = (not finished or row["host_vehicle_corrections"] > 0 or row["guest_vehicle_corrections"] > 0
                            or row.get("guest2_vehicle_corrections", 0) > 0
                            or abs(row.get("guest_minus_host_credited_ms", 0)) > 150
                            or abs(row.get("guest2_minus_host_credited_ms", 0)) > 150)
    if field_samples:
        watched = {}
        longest_stall = {}
        last_positions = {}
        travelled = {}
        uncredited = {}
        for sample in field_samples:
            if sample["track"] != event["track"] or sample["ticks"] <= 300:
                continue
            for name, lap, progress, state in re.findall(r"([^=]+?)=(\d+)\+([0-9.]+|\?)([FX]?)\s*", sample["progress"]):
                name = name.strip()
                if name.startswith("Latency") or progress == "?" or state:
                    continue
                lap, progress = int(lap), float(progress)
                old_lap, old_progress = last_positions.get(name, (lap, progress))
                if old_progress > .8 and progress < .2 and lap == old_lap:
                    uncredited[name] = uncredited.get(name, 0) + 1
                value = travelled.get(name, float(lap) + old_progress) + (progress - old_progress + .5) % 1 - .5
                travelled[name] = value
                last_positions[name] = (lap, progress)
                previous, moved_at = watched.get(name, (value, sample["ticks"]))
                if value > previous + .005:
                    previous, moved_at = value, sample["ticks"]
                watched[name] = (previous, moved_at)
                longest_stall[name] = max(longest_stall.get(name, 0), sample["ticks"] - moved_at)
        row["ai_longest_stagnation_ticks"] = longest_stall
        row["ai_stall_flags"] = [name for name, duration in longest_stall.items() if duration > 600]
        row["ai_uncredited_laps"] = uncredited
        row["review_needed"] |= bool(row["ai_stall_flags"] or uncredited)
        durations = sorted(r["server_tick_ms"] for r in field_samples if r["track"] == event["track"])
        if durations:
            row["sampled_server_tick_ms_p95"] = round(durations[min(len(durations) - 1, int(len(durations) * .95))], 2)
            row["sampled_server_tick_ms_max"] = round(max(durations), 2)
    summary.append(row)
(out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
print(json.dumps(summary, indent=2))
config = json.loads((out / "config.json").read_text())
all_tracks = "C_MEADOW,C_ORCHARD,C_SHORE,C_DOWNS,C_CIDER,C_LAGOON,B_CANYON,B_FORD,B_FROST,B_MESA,B_RAPIDS,B_GLACIER,A_CRYSTAL,A_CANOPY,A_EMBER,A_DEEPS,A_TEMPLE,A_INFERNO,S_SKYWAY,S_KEEP,S_VOID,S_STARFALL,S_CITADEL,S_MAELSTROM".split(",")
expected = set(all_tracks if config["tracks"] == "ALL" else config["tracks"].split(","))
actual = {row["track"] for row in summary}
coverage = {"expected": len(expected), "completed": len(actual), "missing": sorted(expected - actual),
            "flagged_tracks": [row["track"] for row in summary if row["review_needed"]],
            "errors": [e for e in events if e.get("event") == "error"],
            "expected_humans": config.get("humans", 2), "expected_ai": config.get("ai", 0)}
for row in summary:
    if config.get("strict_ai_stalls") and row.get("ai_stall_flags"):
        coverage["errors"].append({"track": row["track"], "reason": "AI stopped progressing for over 30 seconds", "racers": row["ai_stall_flags"]})
    if config.get("strict_ai_stalls") and row.get("ai_uncredited_laps"):
        coverage["errors"].append({"track": row["track"], "reason": "AI crossed the line without lap credit", "racers": row["ai_uncredited_laps"]})
    if config.get("strict_corrections") and any(row.get(role + "_vehicle_corrections", 0) for role in ("host", "guest", "guest2")):
        coverage["errors"].append({"track": row["track"], "reason": "vehicle corrections during race"})
    if config.get("humans", 2) == 3 and not row["telemetry_complete"]:
        coverage["errors"].append({"track": row["track"], "reason": "missing human client telemetry"})
for event in ends:
    start = next((e for e in events if e.get("event") == "start" and e["track"] == event["track"]), {})
    if "laps" in start and any(event[role]["laps"] != start["laps"] for role in ("host", "guest", "guest2") if role in event):
        coverage["errors"].append({"track": event["track"], "reason": "incorrect human lap count"})
    places = [event[role]["place"] for role in ("host", "guest", "guest2") if role in event]
    if len(places) != len(set(places)):
        coverage["errors"].append({"track": event["track"], "reason": "duplicate human placing"})
    if config.get("humans", 2) == 3 and ("guest2" not in event or event.get("field") != 6):
        coverage["errors"].append({"track": event["track"], "reason": "expected three-player six-racer field missing"})
(out / "coverage.json").write_text(json.dumps(coverage, indent=2) + "\n")
if actual != expected or len(summary) != len(expected) or coverage["errors"] or not all(row["both_finished"] for row in summary):
    sys.exit(1)
