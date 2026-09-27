"""Summarize actual race harness telemetry. Fail if a scenario timed out or the rider did not finish."""
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build/latency/results"
events = [json.loads(line) for line in (out / "events.jsonl").read_text().splitlines()]
ends = [e for e in events if e.get("event") == "end"]
rows = [line.split(",") for line in (out / "server.csv").read_text().splitlines()]
client = [line.split(",") for line in (out / "client.csv").read_text().splitlines()]
end_file = out / "client-end-LatencyRider.jsonl"
client_ends = [json.loads(line) for line in end_file.read_text().splitlines()] if end_file.exists() else []
summary = []
for i, event in enumerate(ends, 1):
    samples = [r for r in rows if r[0] == event["profile"]]
    running = [r for r in samples if r[8] == "false"]
    local = [r for r in client if int(r[0]) == i]
    end_samples = [e for e in client_ends if e.get("run") == i]
    placed = [r for r in running if r[9].split()[0].endswith("F")]
    summary.append({
        "profile": event["profile"],
        "finished": not event["timeout"] and event["report"].split()[0].endswith("F"),
        "telemetry_complete": bool(local and end_samples),
        "session_ticks": event["ticks"],
        "vehicle_corrections": max([int(r[10]) for r in local if len(r) > 10]
                                   + [e["vehicle_corrections"] for e in end_samples] + [0]),
        "first_placed_sample_tick": int(placed[0][1]) if placed else None,
        "client_last_running_sample_tick": int(local[-1][1]) if local else None,
        "observed_rtt_ms_min_max": [min(int(r[2]) for r in running), max(int(r[2]) for r in running)] if running else None,
        "race_probe_rtt_ms_min_max": [min(int(r[10]) for r in running), max(int(r[10]) for r in running)] if running and len(running[0]) > 10 else None,
        "stamina_min": min(int(r[6]) for r in running) if running else None,
        "dash_locked_samples": sum(r[7] == "true" for r in running),
    })
(out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
print(json.dumps(summary, indent=2))
config_file = out / "config.json"
config = json.loads(config_file.read_text()) if config_file.exists() else {}
expected = config.get("profiles", ["baseline", "120ms", "300ms", "jitter"])
if ([s["profile"] for s in summary] != expected or not all(s["finished"] for s in summary)
        or (config.get("strict_corrections") and any(s["vehicle_corrections"] or not s["telemetry_complete"] for s in summary))):
    sys.exit(1)
