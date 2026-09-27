"""Race an integrated host, two delayed guests, and three real AI on every C/B/A/S track.

python tools/paired_race_harness.py --rtt 150
python tools/paired_race_harness.py --tracks B_FORD,S_KEEP --rtt 300
Requires the seed world created by tools/latency_harness.py and Python nbtlib.
"""
from contextlib import ExitStack
from functools import partial
import argparse
import asyncio
import json
import hashlib
from datetime import datetime, timezone
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import nbtlib
import latency_harness as common

ROOT = common.ROOT
BASE = ROOT / "build/latency-pair"
OUT = BASE / "results"


def options(folder):
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "options.txt").write_text(
        "onboardAccessibility:false\npauseOnLostFocus:false\ntutorialStep:none\n"
        "skipMultiplayerWarning:true\njoinedFirstServer:true\nrenderDistance:6\n"
        "simulationDistance:6\nguiScale:2\nmaxFps:60\nsoundCategory_master:0.0\n")


def launch(role, tracks, log, duel=False, grid_rotation=0, trace_track=""):
    template = ROOT / "build/moddev/latencyClientRunProgramArgs.txt"
    args = template.read_text().replace("LatencyRider", {"host": "LatencyHost", "guest": "LatencyGuest", "guest2": "LatencyGuest2"}[role])
    if role == "host":
        args = args.replace("--quickPlayMultiplayer", "--quickPlaySingleplayer").replace("127.0.0.1:25579", "latency-host")
    if role == "guest2":
        args = args.replace("127.0.0.1:25579", "127.0.0.1:25580")
    argfile = BASE / role / "program-args.txt"
    argfile.write_text(args)
    command = json.loads((ROOT / "build/latency/client/launch.json").read_text())
    command[-1] = "@" + argfile.as_posix()
    index = command.index("-cp")
    command[index:index] = ["-Dchocobosreborn.harness=" + ("host" if role == "host" else "client"),
                             "-Dchocobosreborn.harness.output=" + str(OUT),
                             "-Dchocobosreborn.harness.fullField=" + str(not duel).lower(),
                             "-Dchocobosreborn.harness.gridRotation=" + str(grid_rotation),
                             "-Dchocobosreborn.harness.traceTrack=" + trace_track]
    if tracks:
        command[index:index] = ["-Dchocobosreborn.harness.tracks=" + tracks]
    return subprocess.Popen(command, cwd=BASE / role, env=common.runtime_env(), stdout=log, stderr=subprocess.STDOUT,
                            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)


async def main(args):
    roles = ("host", "guest") if args.duel else ("host", "guest", "guest2")
    BASE.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        archive = BASE / ("results-" + time.strftime("%Y%m%d-%H%M%S"))
        OUT.rename(archive)
        for role in ("host", "guest", "guest2"):
            if (BASE / (role + ".log")).exists():
                shutil.copy2(BASE / (role + ".log"), archive / (role + ".log"))
            if (BASE / role / "screenshots").exists():
                shutil.copytree(BASE / role / "screenshots", archive / (role + "-screenshots"))
    OUT.mkdir()
    (OUT / "network.json").write_text(json.dumps({"delay_ms": args.rtt / 2, "jitter_ms": 0}))
    (OUT / "network2.json").write_text(json.dumps({"delay_ms": args.rtt2 / 2, "jitter_ms": args.jitter2, "burst_ms": args.burst2}))
    source = hashlib.sha256()
    for path in sorted((ROOT / "src/main").rglob("*")):
        if path.is_file() and not ({"harness", "gametest"} & set(path.parts)):
            source.update(path.relative_to(ROOT).as_posix().encode())
            source.update(path.read_bytes())
    (OUT / "config.json").write_text(json.dumps({"tracks": args.tracks or "ALL", "guest_added_rtt_ms": args.rtt,
        "guest2_added_rtt_ms": None if args.duel else args.rtt2, "guest2_one_way_jitter_ms": args.jitter2,
        "guest2_burst_ms": args.burst2, "humans": len(roles), "ai": 0 if args.duel else 3, "grid_rotation": args.grid_rotation, "trace_track": args.trace_track, "strict_corrections": True,
        "strict_ai_stalls": True, "host": "integrated", "started_utc": datetime.now(timezone.utc).isoformat(),
        "production_source_sha256": source.hexdigest(), "bird_class": "same as course",
        "bird": "class bird: C yellow, B green or blue, A black or flame, S gold, field training"}, indent=2))
    common.OUT = OUT
    for role in roles:
        options(BASE / role)
    world = BASE / "host/saves/latency-host"
    if not world.exists():
        source = ROOT / "build/latency/server/latency-world"
        if not (source / "level.dat").exists():
            raise RuntimeError("Run tools/latency_harness.py first to create the isolated seed world")
        shutil.copytree(source, world)
    level = nbtlib.load(world / "level.dat")
    data = level["Data"]
    data["confirmedExperimentalSettings"] = nbtlib.Byte(1)
    data["allowCommands"] = nbtlib.Byte(1)
    data["LevelName"] = nbtlib.String("Latency host QA")
    data["Difficulty"] = nbtlib.Byte(0)
    data.pop("Player", None)
    level.save()
    wrapper = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    with (BASE / "prepare.log").open("w") as log:
        process = await asyncio.create_subprocess_exec(wrapper, "prepareLatencyHarness", "--console=plain",
                cwd=ROOT, env=common.runtime_env(), stdout=log, stderr=subprocess.STDOUT)
        if await process.wait():
            raise RuntimeError("Preparation failed; see build/latency-pair/prepare.log")
    processes = []
    with ExitStack() as stack:
        logs = {role: stack.enter_context((BASE / (role + ".log")).open("w")) for role in roles}
        proxies = [await asyncio.start_server(common.proxy, "127.0.0.1", 25579)]
        if not args.duel:
            proxies.append(await asyncio.start_server(partial(common.proxy, network_path=OUT / "network2.json"), "127.0.0.1", 25580))
        try:
            host = launch("host", args.tracks, logs["host"], args.duel, args.grid_rotation, args.trace_track)
            processes.append(host)
            for _ in range(180):
                if (OUT / "listening.txt").exists():
                    break
                if host.poll() is not None:
                    raise RuntimeError("Host exited before opening the test connection; inspect host.log")
                await asyncio.sleep(2)
            else:
                raise TimeoutError("Integrated host startup timed out")
            for role in roles[1:]:
                processes.append(launch(role, args.tracks, logs[role], args.duel, args.grid_rotation, args.trace_track))
            last = ""
            for poll in range(7200):
                path = OUT / "events.jsonl"
                text = path.read_text() if path.exists() else ""
                if text != last:
                    print(text[len(last):], end="", flush=True)
                    last = text
                if (OUT / "done.txt").exists():
                    for process in processes:
                        try:
                            await asyncio.to_thread(process.wait, 30)
                        except subprocess.TimeoutExpired:
                            pass
                    result = subprocess.run([sys.executable, str(ROOT / "tools/analyze_paired_races.py"), str(OUT)],
                                            cwd=ROOT, stdout=subprocess.DEVNULL)
                    if result.returncode:
                        raise RuntimeError("Paired race checks failed; see results/coverage.json and results/summary.json")
                    coverage = json.loads((OUT / "coverage.json").read_text())
                    print(json.dumps({"completed": coverage["completed"], "review_tracks": coverage["flagged_tracks"]}), flush=True)
                    return
                if poll >= 180 and not text:
                    raise TimeoutError("Not all race clients joined within six minutes; inspect client logs")
                if any(p.poll() is not None for p in processes):
                    raise RuntimeError("Host or guest closed before the suite finished; inspect both logs")
                await asyncio.sleep(2)
            raise TimeoutError("Paired suite exceeded four hours")
        finally:
            # Give the integrated host a chance to save its isolated world on errors too.
            if any(process.poll() is None for process in processes):
                if not (OUT / "done.txt").exists():
                    (OUT / "done.txt").write_text("aborted; see launcher error and logs\n")
                for process in processes:
                    if process.poll() is None:
                        try:
                            await asyncio.to_thread(process.wait, 20)
                        except subprocess.TimeoutExpired:
                            pass
            for proxy in proxies:
                proxy.close()
                await proxy.wait_closed()
            for process in reversed(processes):
                if process.poll() is None:
                    process.terminate()
                    try:
                        await asyncio.to_thread(process.wait, 20)
                    except subprocess.TimeoutExpired:
                        process.kill()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tracks", default="", help="Comma-separated RaceTrack names; default all 24")
    parser.add_argument("--rtt", type=int, default=150, help="Added guest round-trip delay in milliseconds")
    parser.add_argument("--rtt2", type=int, default=300, help="Second guest added RTT in milliseconds")
    parser.add_argument("--jitter2", type=int, default=0, help="Second guest one-way jitter (+/- ms)")
    parser.add_argument("--burst2", type=int, default=0, help="Second guest periodic stall in milliseconds")
    parser.add_argument("--trace-track", default="", help="One track to record every client tick and nearby boost-pad geometry")
    parser.add_argument("--grid-rotation", type=int, choices=(0, 1, 2), default=0, help="Rotate human starting slots to separate grid-position effects from ping")
    parser.add_argument("--duel", action="store_true", help="Retain the two-player/no-AI comparison")
    args = parser.parse_args()
    args.tracks = args.tracks.upper()
    args.trace_track = args.trace_track.upper()
    if any(not 0 <= value <= 2000 for value in (args.rtt, args.rtt2, args.jitter2, args.burst2)):
        parser.error("rtt must be between 0 and 2000 ms")
    if args.tracks and not args.tracks.replace(",", "").replace("_", "").isalnum():
        parser.error("tracks must be comma-separated enum names")
    asyncio.run(main(args))
