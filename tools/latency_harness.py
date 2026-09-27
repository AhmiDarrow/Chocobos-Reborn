"""Run real client races through an ordered TCP delay proxy. Isolated worlds under build/latency.

python tools/latency_harness.py
Requires Java 21 and a working desktop OpenGL driver. No input automation dependencies.
The server is offline-mode and binds exclusively to loopback. All harness Java is excluded from the jar.
"""
import asyncio
import argparse
import json
import hashlib
from datetime import datetime, timezone
import os
from pathlib import Path
import random
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "build/latency"
OUT = Path(os.environ["LATENCY_RESULTS"]) if os.environ.get("LATENCY_RESULTS") else BASE / "results"
TRACK = "C_MEADOW"
UPSTREAM_HOST = os.environ.get("LATENCY_UPSTREAM_HOST", "127.0.0.1")
UPSTREAM_PORT = int(os.environ.get("LATENCY_UPSTREAM_PORT", "25578"))


async def relay(reader, writer, network_path=None):
    queue = asyncio.Queue(maxsize=4096)
    rng = random.Random(20260926)

    async def receive():
        while data := await reader.read(65536):
            try:
                config = json.loads((network_path or OUT / "network.json").read_text())
            except (OSError, ValueError):
                config = {}
            delay = max(0, config.get("delay_ms", 0) + rng.uniform(-config.get("jitter_ms", 0), config.get("jitter_ms", 0)))
            if time.monotonic() % 10 < 0.2:
                delay += config.get("burst_ms", 0)
            await queue.put((time.monotonic() + delay / 1000, data))
        await queue.put((0, None))

    async def send():
        while True:
            due, data = await queue.get()
            if data is None:
                break
            await asyncio.sleep(max(0, due - time.monotonic()))
            writer.write(data)
            await writer.drain()

    tasks = [asyncio.create_task(receive()), asyncio.create_task(send())]
    try:
        await asyncio.gather(*tasks)
    finally:
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)
        writer.close()


async def proxy(reader, writer, network_path=None):
    upstream_reader, upstream_writer = await asyncio.open_connection(UPSTREAM_HOST, UPSTREAM_PORT)
    try:
        await asyncio.gather(relay(reader, upstream_writer, network_path), relay(upstream_reader, writer, network_path))
    except (ConnectionError, OSError):
        pass


def runtime_env():
    env = os.environ.copy()
    temp = ROOT / "build/tmp/net"
    temp.mkdir(parents=True, exist_ok=True)
    env["JAVA_TOOL_OPTIONS"] = (env.get("JAVA_TOOL_OPTIONS", "") + ' -Djdk.net.unixdomain.tmpdir="' + str(temp) + '"'
                               + " -Dchocobosreborn.harness.track=" + TRACK)
    return env


def point_client_results():
    """Send the dev client at the same results folder the dedicated server writes."""
    if not os.environ.get("LATENCY_RESULTS"):
        return
    vm = ROOT / "build/moddev/latencyClientRunVmArgs.txt"
    lines = [line for line in vm.read_text(encoding="utf-8").splitlines()
             if not line.startswith("-Dchocobosreborn.harness.output=")]
    lines.append("-Dchocobosreborn.harness.output=" + str(OUT).replace("\\", "\\\\"))
    vm.write_text("\n".join(lines) + "\n", encoding="utf-8")


def launch(side, log):
    command = json.loads((BASE / side / "launch.json").read_text())
    return subprocess.Popen(command, cwd=BASE / side, env=runtime_env(), stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
                            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)


async def main():
    for folder in (OUT, BASE / "server", BASE / "client"):
        folder.mkdir(parents=True, exist_ok=True)
    if (OUT / "events.jsonl").exists():
        archive = BASE / ("results-" + time.strftime("%Y%m%d-%H%M%S"))
        OUT.rename(archive)
        OUT.mkdir()
        for name in ("server.log", "client.log"):
            if (BASE / name).exists():
                shutil.copy2(BASE / name, archive / name)
        if (BASE / "client/screenshots").exists():
            shutil.copytree(BASE / "client/screenshots", archive / "screenshots")
    (BASE / "server/eula.txt").write_text("eula=true\n")
    (BASE / "server/server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=25578\nonline-mode=false\nlevel-name=latency-world\n"
        'level-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\n'
        "spawn-protection=0\nview-distance=6\nsimulation-distance=6\n"
        "max-players=2\nallow-flight=true\nmotd=Local latency harness\n")
    (BASE / "client/options.txt").write_text(
        "onboardAccessibility:false\npauseOnLostFocus:false\ntutorialStep:none\n"
        "skipMultiplayerWarning:true\njoinedFirstServer:true\nrenderDistance:6\n"
        "simulationDistance:6\nguiScale:2\nmaxFps:60\nsoundCategory_master:0.0\n")
    source = hashlib.sha256()
    for path in sorted((ROOT / "src/main").rglob("*")):
        if path.is_file() and not ({"harness", "gametest"} & set(path.parts)):
            source.update(path.relative_to(ROOT).as_posix().encode())
            source.update(path.read_bytes())
    (OUT / "config.json").write_text(json.dumps({"track": TRACK,
        "profiles": ["baseline", "120ms", "300ms", "jitter", "burst"], "strict_corrections": True,
        "host": "dedicated", "humans": 1, "ai": 5, "started_utc": datetime.now(timezone.utc).isoformat(),
        "production_source_sha256": source.hexdigest(),
        "bird": {"color": "GOLD", "grade": "WONDERFUL", "class": "S", "speed": 50, "stamina": 100, "intelligence": 0, "cooperation": 100}}, indent=2) + "\n")
    processes = []
    wrapper = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    with (BASE / "prepare.log").open("w") as prepare_log:
        prepared = await asyncio.create_subprocess_exec(wrapper, "prepareLatencyHarness", "--console=plain",
                cwd=ROOT, env=runtime_env(), stdout=prepare_log, stderr=subprocess.STDOUT)
        if await prepared.wait() != 0:
            raise RuntimeError("Harness preparation failed; see build/latency/prepare.log")
    point_client_results()
    with (BASE / "server.log").open("w") as server_log, (BASE / "client.log").open("w") as client_log:
        proxy_server = await asyncio.start_server(proxy, "127.0.0.1", 25579)
        try:
            server = None
            if os.environ.get("LATENCY_SKIP_SERVER") == "1":
                for _ in range(180):
                    try:
                        probe_reader, probe_writer = await asyncio.open_connection(UPSTREAM_HOST, UPSTREAM_PORT)
                    except OSError:
                        await asyncio.sleep(2)
                        continue
                    probe_writer.close()
                    await probe_writer.wait_closed()
                    break
                else:
                    raise TimeoutError("Remote server did not accept a connection")
            else:
                server = launch("server", server_log)
                processes.append(server)
                for _ in range(180):
                    if server.poll() is not None:
                        raise RuntimeError("Server exited; see build/latency/server.log")
                    if 'For help, type "help"' in (BASE / "server.log").read_text(errors="replace"):
                        break
                    await asyncio.sleep(2)
                else:
                    raise TimeoutError("Server startup timed out")
            client = launch("client", client_log)
            processes.append(client)
            for _ in range(1200):
                if (OUT / "done.txt").exists():
                    print((OUT / "events.jsonl").read_text(), flush=True)
                    await asyncio.to_thread(client.wait, 30)
                    result = subprocess.run([sys.executable, str(ROOT / "tools/analyze_latency.py"), str(OUT)],
                                            cwd=ROOT, stdout=subprocess.DEVNULL)
                    if result.returncode:
                        raise RuntimeError("Dedicated race checks failed; see results/summary.json")
                    return
                if client.poll() is not None or (server is not None and server.poll() is not None):
                    raise RuntimeError("Game exited; inspect client.log/server.log")
                await asyncio.sleep(2)
            raise TimeoutError("Harness timed out")
        finally:
            proxy_server.close()
            await proxy_server.wait_closed()
            for process in reversed(processes):
                if process.poll() is None:
                    if server is not None and process is server:
                        try:
                            process.stdin.write(b"stop\n")
                            process.stdin.flush()
                            await asyncio.to_thread(process.wait, 20)
                            continue
                        except (OSError, subprocess.TimeoutExpired):
                            pass
                    if os.name == "nt":
                        subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True)
                    else:
                        process.terminate()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--track", default="C_MEADOW", help="RaceTrack enum name, e.g. C_SHORE or B_FORD")
    TRACK = parser.parse_args().track.upper()
    if not TRACK.replace("_", "").isalnum():
        parser.error("track must be a RaceTrack enum name")
    asyncio.run(main())
