"""A crowded heat with real clients: a dedicated server, N real game clients, AI filling the field.

python tools/crowd_race_harness.py                         # 10 clients, 20 birds, C_MEADOW
python tools/crowd_race_harness.py --clients 3 --field 6   # a quick check first
python tools/crowd_race_harness.py --baseline              # the same with vanilla movement handling (kill switches)

Every client is a real, rendered game joining through its own delay proxy with its own link: from a
near-local 20 ms round trip up to about 300 ms, with jitter, two with periodic stalls. The server
(RaceHarnessCrowd in its real-client mode) waits for all of them, races them with the AI, and the
harness client (RaceHarnessClient) drives each bird and records what that client saw: motion-<name>.csv,
frames-<name>.csv, client-<name>.csv. Results: build/latency-crowd/results, then
tools/analyze_smoothness.py runs on them.

Runs on this PC and is careful with it: clients below-normal priority, capped at 60 fps, 2 GB heap,
render distance 6, small windows, launched a few seconds apart; everything is stopped at the end.
Requires the seed world and launch files from tools/latency_harness.py (run it once first).
"""
import argparse
import asyncio
from functools import partial
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

import latency_harness as common

ROOT = common.ROOT
BASE = ROOT / "build/latency-crowd"
# The dedicated server writes its results where its run configuration says: build/latency/results.
SERVER_OUT = ROOT / "build/latency/results"
OUT = BASE / "results"
SERVER_PORT = 25578
FIRST_PROXY = 25590
# one-way delay ms, jitter +- ms, stall ms every 10 s (the proxy's burst)
LINKS = [
    (10, 2, 0), (25, 5, 0), (40, 10, 0), (60, 15, 0), (75, 20, 0),
    (100, 30, 250), (125, 40, 0), (150, 50, 0), (60, 30, 400), (35, 8, 0),
]
BELOW_NORMAL = getattr(subprocess, "BELOW_NORMAL_PRIORITY_CLASS", 0)
NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0)


def names(count):
    return ["Racer%02d" % (i + 1) for i in range(count)]


def options(folder):
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "options.txt").write_text(
        "onboardAccessibility:false\npauseOnLostFocus:false\ntutorialStep:none\n"
        "skipMultiplayerWarning:true\njoinedFirstServer:true\nrenderDistance:6\n"
        "simulationDistance:6\nguiScale:2\nmaxFps:60\nsoundCategory_master:0.0\n")


def server_env(args, roster):
    env = common.runtime_env()
    extra = (" -Dchocobosreborn.harness.crowd=%d -Dchocobosreborn.harness.crowdNames=%s -Dchocobosreborn.race.field=%d"
             % (len(roster), ",".join(roster), args.field))
    if args.baseline:
        extra += " -Dchocobosreborn.vanillaRiderMovement=true"
    env["JAVA_TOOL_OPTIONS"] = env.get("JAVA_TOOL_OPTIONS", "") + extra
    return env


def client_env(args):
    env = common.runtime_env()
    if args.baseline:
        env["JAVA_TOOL_OPTIONS"] = env.get("JAVA_TOOL_OPTIONS", "") + " -Dchocobosreborn.vanillaFieldPlayback=true"
    return env


def launch_server(args, roster, log):
    command = json.loads((ROOT / "build/latency/server/launch.json").read_text())
    index = command.index("-cp")
    command[index:index] = ["-Xmx4G"]
    return subprocess.Popen(command, cwd=ROOT / "build/latency/server", env=server_env(args, roster), stdin=subprocess.PIPE,
                            stdout=log, stderr=subprocess.STDOUT, creationflags=NO_WINDOW)


def launch_client(args, name, port, log):
    folder = BASE / name
    options(folder)
    template = (ROOT / "build/moddev/latencyClientRunProgramArgs.txt").read_text()
    program = (template.replace("LatencyRider", name).replace("127.0.0.1:25579", "127.0.0.1:%d" % port)
               .replace("--width\n1024", "--width\n640").replace("--height\n600", "--height\n360"))
    argfile = folder / "program-args.txt"
    argfile.write_text(program)
    command = json.loads((ROOT / "build/latency/client/launch.json").read_text())
    command[-1] = "@" + argfile.as_posix()
    index = command.index("-cp")
    command[index:index] = ["-Xmx2G", "-Dchocobosreborn.harness=client", "-Dchocobosreborn.harness.output=" + str(SERVER_OUT),
                            "-Dchocobosreborn.harness.track=" + args.track]
    return subprocess.Popen(command, cwd=folder, env=client_env(args), stdout=log, stderr=subprocess.STDOUT,
                            creationflags=BELOW_NORMAL)


async def main(args):
    roster = names(args.clients)
    common.TRACK = args.track
    BASE.mkdir(parents=True, exist_ok=True)
    if SERVER_OUT.exists():
        shutil.rmtree(SERVER_OUT)
    SERVER_OUT.mkdir(parents=True)
    for i, name in enumerate(roster):
        delay, jitter, burst = LINKS[i % len(LINKS)]
        (SERVER_OUT / ("network-%s.json" % name)).write_text(json.dumps({"delay_ms": delay, "jitter_ms": jitter, "burst_ms": burst}))
    (SERVER_OUT / "config.json").write_text(json.dumps({"track": args.track, "clients": roster, "field": args.field,
        "links_one_way_ms_jitter_burst": LINKS[:len(roster)], "baseline": args.baseline}, indent=2))
    server_dir = ROOT / "build/latency/server"
    if not (server_dir / "latency-world/level.dat").exists():
        raise RuntimeError("Run tools/latency_harness.py once first: it creates the isolated server world")
    props = (server_dir / "server.properties").read_text()
    lines = [line for line in props.splitlines() if not line.startswith(("max-players=", "difficulty="))]
    lines.append("max-players=%d" % (len(roster) + 2))
    lines.append("difficulty=peaceful")   # clients join one by one; none may die waiting for the rest
    (server_dir / "server.properties").write_text("\n".join(lines) + "\n")
    wrapper = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    with (BASE / "prepare.log").open("w") as log:
        prepared = await asyncio.create_subprocess_exec(wrapper, "prepareLatencyHarness", "--console=plain", "--no-daemon",
                cwd=ROOT, env=common.runtime_env(), stdout=log, stderr=subprocess.STDOUT)
        if await prepared.wait():
            raise RuntimeError("Harness preparation failed; see build/latency-crowd/prepare.log")
    processes, logs, proxies = [], [], []
    try:
        server_log = (BASE / "server.log").open("w")
        logs.append(server_log)
        server = launch_server(args, roster, server_log)
        processes.append(server)
        for _ in range(240):
            if server.poll() is not None:
                raise RuntimeError("Server exited; see build/latency-crowd/server.log")
            if 'For help, type "help"' in (BASE / "server.log").read_text(errors="replace"):
                break
            await asyncio.sleep(2)
        else:
            raise TimeoutError("Server startup timed out")
        for i, name in enumerate(roster):
            port = FIRST_PROXY + i
            proxies.append(await asyncio.start_server(
                partial(common.proxy, network_path=SERVER_OUT / ("network-%s.json" % name)), "127.0.0.1", port))
            log = (BASE / (name + ".log")).open("w")
            logs.append(log)
            processes.append(launch_client(args, name, port, log))
            print("launched", name, "on proxy", port, "link", LINKS[i % len(LINKS)], flush=True)
            await asyncio.sleep(args.stagger)
        last = ""
        for poll in range(1800):
            path = SERVER_OUT / "events.jsonl"
            text = path.read_text() if path.exists() else ""
            if text != last:
                print(text[len(last):], end="", flush=True)
                last = text
            if (SERVER_OUT / "done.txt").exists():
                for process in processes[1:]:
                    try:
                        await asyncio.to_thread(process.wait, 45)
                    except subprocess.TimeoutExpired:
                        pass
                return
            if server.poll() is not None:
                raise RuntimeError("Server exited mid-run; see build/latency-crowd/server.log")
            dead = [roster[i] for i, p in enumerate(processes[1:]) if p.poll() is not None]
            if dead:
                raise RuntimeError("Client(s) closed before the race finished: %s" % ", ".join(dead))
            await asyncio.sleep(2)
        raise TimeoutError("Crowd race exceeded an hour")
    finally:
        for proxy in proxies:
            proxy.close()
        for process in reversed(processes):
            if process.poll() is None:
                if process is processes[0]:
                    try:
                        process.stdin.write(b"stop\n")
                        process.stdin.flush()
                        await asyncio.to_thread(process.wait, 30)
                        continue
                    except (OSError, subprocess.TimeoutExpired):
                        pass
                if os.name == "nt":
                    subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True)
                else:
                    process.terminate()
        for log in logs:
            log.close()
        if SERVER_OUT.exists():
            if OUT.exists():
                shutil.rmtree(OUT)
            shutil.copytree(SERVER_OUT, OUT)
            for name in ["server"] + roster:
                if (BASE / (name + ".log")).exists():
                    shutil.copy2(BASE / (name + ".log"), OUT / (name + ".log"))
            subprocess.run([sys.executable, str(ROOT / "tools/analyze_smoothness.py"), str(OUT), "--json", str(OUT / "smoothness.json")],
                           cwd=ROOT, stdout=subprocess.DEVNULL)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--clients", type=int, default=10)
    parser.add_argument("--field", type=int, default=20)
    parser.add_argument("--track", default="C_MEADOW")
    parser.add_argument("--stagger", type=int, default=8, help="Seconds between client launches")
    parser.add_argument("--baseline", action="store_true", help="Vanilla movement handling on server and clients")
    asyncio.run(main(parser.parse_args()))
