"""Race three real clients on this PC against a dedicated harness server on the hub PC.

python tools/hub_race_harness.py                      # the 24 phase-2 courses (index 6-11 of each class)
python tools/hub_race_harness.py --tracks C_HARVEST,S_ABYSS
python tools/hub_race_harness.py --all                # all 48

The hub (X99, 192.168.0.13) is reached over SSH with ~/.ssh/id_ed25519 as Superuser, as set up
in ~/.grok/x99-access. The harness server lives in C:\\latency-harness on port 25578 (firewalled
to this PC); the live world on 25565 is never touched: only the java started here, tagged
-Dchocobosreborn.hubMarker, is ever stopped. Six-bird heats: LatencyHost, LatencyGuest and
LatencyGuest2 plus three real AI (RaceHarnessPair in hub mode). Clients run below-normal
priority, 60 fps, render distance 6, staggered. Results land in build/latency-hub/results.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "build/latency-hub"
OUT = BASE / "results"
HUB = os.environ.get("HUB_HOST", "192.168.0.13")
HUB_USER = os.environ.get("HUB_USER", "Superuser")
PORT = 25578
KEY = str(Path.home() / ".ssh/id_ed25519")
REMOTE = r"C:\latency-harness"
MARKER = "-Dchocobosreborn.hubMarker=1"
ROLES = ("host", "guest", "guest2")
NAMES = {"host": "LatencyHost", "guest": "LatencyGuest", "guest2": "LatencyGuest2"}
SSH = ["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=10", "-o", "LogLevel=ERROR", "-i", KEY, f"{HUB_USER}@{HUB}"]
BELOW_NORMAL = getattr(subprocess, "BELOW_NORMAL_PRIORITY_CLASS", 0)


def remote(script, check=True):
    """Run a PowerShell script on the hub; returns stdout."""
    encoded = base64.b64encode(("$ProgressPreference='SilentlyContinue'\n" + script).encode("utf-16-le")).decode()
    result = subprocess.run(SSH + ["powershell -NoProfile -NonInteractive -EncodedCommand " + encoded],
                            capture_output=True, text=True, timeout=120)
    if check and result.returncode:
        raise RuntimeError("hub command failed: " + (result.stderr or result.stdout).strip())
    return result.stdout


def scp(source, target):
    subprocess.run(["scp", "-q", "-o", "BatchMode=yes", "-o", "LogLevel=ERROR", "-i", KEY, source, target], check=True, timeout=600)


def gradle(*tasks):
    log = (BASE / "prepare.log").open("w")
    process = subprocess.Popen([str(ROOT / "gradlew.bat"), *tasks, "--no-daemon", "-Dorg.gradle.workers.max=1", "--console=plain"],
                               cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, creationflags=BELOW_NORMAL)
    if process.wait():
        raise RuntimeError("Gradle failed; see build/latency-hub/prepare.log")


def harness_jar():
    """Production jar plus the dev-only harness classes (excluded from releases)."""
    jars = [p for p in (ROOT / "build/libs").glob("chocobosreborn-*.jar") if not p.stem.endswith(("-sources", "-javadoc"))]
    source = max(jars, key=lambda p: p.stat().st_mtime)
    target = BASE / "chocobosreborn-hub-harness.jar"
    shutil.copy2(source, target)
    classes = ROOT / "build/classes/java/main"
    with zipfile.ZipFile(target, "a") as jar:
        for path in (classes / "tk/darrow/chocobosreborn/harness").rglob("*.class"):
            jar.write(path, path.relative_to(classes).as_posix())
    return target


def default_tracks(everything):
    rows = re.findall(r"^\s*([A-Z]_[A-Z_]+)\(RaceClass\.[A-Z], (\d+),", (ROOT / "src/main/java/tk/darrow/chocobosreborn/race/RaceTrack.java").read_text(encoding="utf-8"), re.M)
    return [name for name, index in rows if everything or int(index) >= 6]


def stop_hub_server():
    remote(f"""
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {{ $_.CommandLine -like '*chocobosreborn.hubMarker*' }} | ForEach-Object {{ & taskkill.exe /PID $_.ProcessId /T /F | Out-Null; 'stopped ' + $_.ProcessId }}
""", check=False)


def start_hub_server(jar, tracks):
    print("hub: uploading", jar.name, flush=True)
    remote(f"""
New-Item -ItemType Directory -Force -Path '{REMOTE}\\mods' | Out-Null
Get-ChildItem '{REMOTE}\\mods' -Filter 'chocobosreborn*' | Remove-Item -Force
if (Test-Path '{REMOTE}\\results-hub') {{ Rename-Item '{REMOTE}\\results-hub' ('results-hub-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }}
New-Item -ItemType Directory -Force -Path '{REMOTE}\\results-hub' | Out-Null
""")
    scp(str(jar), f"{HUB_USER}@{HUB}:C:/latency-harness/mods/{jar.name}")
    props = "\n".join([
        "server-ip=", f"server-port={PORT}", "online-mode=false", "level-name=latency-world", "level-type=minecraft:flat",
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}',
        "spawn-protection=0", "view-distance=6", "simulation-distance=6", "max-players=6", "allow-flight=true",
        "motd=Hub race harness", "enable-rcon=false", "white-list=false", ""])
    jvm = "\n".join(["-Xms2G", "-Xmx4G", "-Dchocobosreborn.harness=hub", "-Dchocobosreborn.harness.tracks=" + ",".join(tracks),
                     "-Dchocobosreborn.harness.fullField=true", "-Dchocobosreborn.harness.output=" + REMOTE + "\\results-hub", ""])
    out = remote(f"""
Set-Content -Path '{REMOTE}\\server.properties' -Value @'
{props}'@ -Encoding ascii
Set-Content -Path '{REMOTE}\\user_jvm_args.txt' -Value @'
{jvm}'@ -Encoding ascii
Set-Content -Path '{REMOTE}\\eula.txt' -Value 'eula=true' -Encoding ascii
Remove-Item '{REMOTE}\\server.log' -ErrorAction SilentlyContinue
$cmd = 'cmd /c "cd /d {REMOTE} && C:\\Java\\jdk-21\\bin\\java.exe {MARKER} @user_jvm_args.txt @libraries\\net\\neoforged\\neoforge\\21.1.249\\win_args.txt nogui > {REMOTE}\\server.log 2>&1"'
$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{{ CommandLine = $cmd; CurrentDirectory = '{REMOTE}' }}
'pid=' + $r.ProcessId + ' ret=' + $r.ReturnValue
""")
    print("hub:", out.strip(), flush=True)
    for _ in range(150):
        time.sleep(4)
        state = remote(f"""
$j = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {{ $_.CommandLine -like '*chocobosreborn.hubMarker*' }}
foreach ($p in $j) {{ $g = Get-Process -Id $p.ProcessId -ErrorAction SilentlyContinue; if ($g) {{ $g.PriorityClass = 'BelowNormal' }} }}
if (-not $j) {{ 'DEAD' }} elseif (Select-String -Path '{REMOTE}\\server.log' -Pattern 'For help, type' -Quiet -ErrorAction SilentlyContinue) {{ 'READY' }} else {{ 'WAIT' }}
""", check=False).strip()
        if state == "READY":
            print("hub: server ready on", f"{HUB}:{PORT}", flush=True)
            return
        if state == "DEAD":
            tail = remote(f"Get-Content '{REMOTE}\\server.log' -Tail 30", check=False)
            raise RuntimeError("hub server exited:\n" + tail)
    raise TimeoutError("hub server did not start in 10 minutes")


def launch_client(role, tracks, log):
    folder = BASE / role
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "options.txt").write_text(
        "onboardAccessibility:false\npauseOnLostFocus:false\ntutorialStep:none\n"
        "skipMultiplayerWarning:true\njoinedFirstServer:true\nrenderDistance:6\n"
        "simulationDistance:6\nguiScale:2\nmaxFps:60\nsoundCategory_master:0.0\n")
    args = (ROOT / "build/moddev/latencyClientRunProgramArgs.txt").read_text()
    args = args.replace("LatencyRider", NAMES[role]).replace("127.0.0.1:25579", f"{HUB}:{PORT}")
    argfile = folder / "program-args.txt"
    argfile.write_text(args)
    command = json.loads((ROOT / "build/latency/client/launch.json").read_text())
    command[-1] = "@" + argfile.as_posix()
    index = command.index("-cp")
    command[index:index] = ["-Dchocobosreborn.harness=client", "-Dchocobosreborn.harness.output=" + str(OUT),
                            "-Dchocobosreborn.harness.tracks=" + ",".join(tracks), "-Xmx3G"]
    return subprocess.Popen(command, cwd=folder, stdout=log, stderr=subprocess.STDOUT,
                            creationflags=BELOW_NORMAL | (subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0))


def main(args):
    tracks = [t.strip().upper() for t in args.tracks.split(",") if t.strip()] or default_tracks(args.all)
    BASE.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        OUT.rename(BASE / ("results-" + time.strftime("%Y%m%d-%H%M%S")))
    OUT.mkdir()
    (OUT / "config.json").write_text(json.dumps({"host": "dedicated hub " + HUB, "tracks": ",".join(tracks), "humans": 3, "ai": 3,
                                                  "started": time.strftime("%Y-%m-%d %H:%M:%S")}, indent=2))
    print(f"{len(tracks)} heats:", ",".join(tracks), flush=True)
    gradle("jar", "prepareLatencyHarness")
    jar = harness_jar()
    stop_hub_server()
    clients = []
    logs = []
    try:
        start_hub_server(jar, tracks)
        for i, role in enumerate(ROLES):
            if i:
                time.sleep(args.stagger)
            logs.append((BASE / (role + ".log")).open("w"))
            clients.append(launch_client(role, tracks, logs[-1]))
            print("client:", NAMES[role], "launched", flush=True)
        seen = 0
        started = time.monotonic()
        while True:
            time.sleep(15)
            text = remote(f"if (Test-Path '{REMOTE}\\results-hub\\events.jsonl') {{ Get-Content '{REMOTE}\\results-hub\\events.jsonl' -Raw }}", check=False)
            lines = [line for line in text.splitlines() if line.strip()]
            for line in lines[seen:]:
                print(line, flush=True)
            seen = max(seen, len(lines))
            done = remote(f"if (Test-Path '{REMOTE}\\results-hub\\done.txt') {{ Get-Content '{REMOTE}\\results-hub\\done.txt' }}", check=False).strip()
            if done:
                print("hub: done:", done, flush=True)
                break
            if not seen and time.monotonic() - started > 600:
                raise TimeoutError("no heat started within ten minutes; see build/latency-hub/*.log")
            dead = [NAMES[r] for r, c in zip(ROLES, clients) if c.poll() is not None]
            if dead:
                raise RuntimeError("client closed early: " + ", ".join(dead))
    finally:
        (OUT / "done.txt").write_text("stop\n")  # the clients close themselves on this file
        for client in clients:
            try:
                client.wait(45)
            except subprocess.TimeoutExpired:
                subprocess.run(["taskkill", "/PID", str(client.pid), "/T", "/F"], capture_output=True)
        for log in logs:
            log.close()
        server_out = OUT / "server"
        server_out.mkdir(exist_ok=True)
        for name in ("events.jsonl", "server.csv", "field.jsonl", "done.txt"):
            try:
                scp(f"{HUB_USER}@{HUB}:C:/latency-harness/results-hub/{name}", str(server_out / name))
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
                pass
        try:
            scp(f"{HUB_USER}@{HUB}:C:/latency-harness/server.log", str(BASE / "server.log"))
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
            pass
        stop_hub_server()
        print("hub: harness server stopped", flush=True)
    for name in ("events.jsonl", "server.csv", "field.jsonl"):
        if (server_out / name).exists():
            shutil.copy2(server_out / name, OUT / name)
    result = subprocess.run([sys.executable, str(ROOT / "tools/analyze_paired_races.py"), str(OUT)], cwd=ROOT,
                            capture_output=True, text=True)
    print("analyzer exit", result.returncode, (result.stdout + result.stderr).strip()[-1500:], flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tracks", default="", help="Comma-separated RaceTrack names; default the 24 phase-2 courses")
    parser.add_argument("--all", action="store_true", help="All 48 courses")
    parser.add_argument("--stagger", type=int, default=25, help="Seconds between client launches")
    main(parser.parse_args())
