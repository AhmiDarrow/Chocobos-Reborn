"""Headless visual harness: boot the real client under Xvfb, run chat commands,
grab screenshots. Linux only (Xvfb + Mesa llvmpipe + xdotool + ImageMagick).

    python tools/visual_harness.py [--keep] [--shots art/harness]

Uses the `visual` run config (build.gradle): --quickPlaySingleplayer harness,
so run/saves/harness must exist (copy run/world from a dedicated-server boot).
Each scene below is a list of chat commands followed by a screenshot name.
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOG = Path(os.environ.get("HARNESS_LOG", "/tmp/chocobosreborn_harness.log"))
DISPLAY = ":99"
W, H = 1024, 600
SHOTS = ROOT / "art/harness"

FARM = (0, 70, 0)      # template origin; found at runtime from Esther's position (template (15,2,12))
X, Y, Z = FARM
PEN_Z = Z + 18            # inside the paddock (fence ring z 14..22)


def scenes():
    """Built after locate_farm() so the coordinates are live."""
    global PEN_Z
    PEN_Z = Z + 18
    return [

    ("setup", ["/gamemode creative", "/time set noon", "/weather clear", "/gamerule doDaylightCycle false",
               "/gamerule doMobSpawning false"], None),
    ("farm_overview", [f"/tp @s {X + 13} {Y + 12} {Z + 40} 180 22"], "farm_overview"),
    ("farm_esther", [f"/tp @s {X + 15} {Y + 3} {Z + 18} 180 8"], "farm_esther"),
    ("birds_row", [f"/kill @e[type=chocobosreborn:chocobo,distance=..60]",
                   f"/summon chocobosreborn:chocobo {X + 5} {Y + 2} {PEN_Z} {{Plumage:0,Male:1b,NoAI:1b,Rotation:[180f,0f]}}",
                   f"/summon chocobosreborn:chocobo {X + 9} {Y + 2} {PEN_Z} {{Plumage:0,Male:0b,NoAI:1b,Rotation:[180f,0f]}}",
                   f"/summon chocobosreborn:chocobo {X + 13} {Y + 2} {PEN_Z} {{Plumage:4,Male:1b,Saddled:1b,NoAI:1b,Rotation:[180f,0f]}}",
                   f"/summon chocobosreborn:chocobo {X + 17} {Y + 2} {PEN_Z} {{Plumage:2,Male:0b,Saddled:1b,NoAI:1b,Rotation:[180f,0f]}}",
                   f"/summon chocobosreborn:chocobo {X + 20} {Y + 2} {PEN_Z} {{Plumage:0,NoAI:1b,Age:-20000,Rotation:[180f,0f]}}",
                   f"/tp @s {X + 12} {Y + 4} {PEN_Z + 7} 180 14"], "birds_row"),
    ("bird_close", [f"/tp @s {X + 9} {Y + 3} {PEN_Z + 4} 180 12"], "bird_close"),
    ("bird_side", [f"/tp @s {X - 3} {Y + 3} {PEN_Z} -90 8"], "bird_side"),
    ("bird_front", [f"/tp @s {X + 11} {Y + 4} {PEN_Z - 9} 0 8"], "bird_front"),
    ("bird_back", [f"/tp @s {X + 11} {Y + 4} {PEN_Z + 9} 180 8"], "bird_back"),
    ("bird_side_black", [f"/tp @s {X + 9} {Y + 3} {PEN_Z + 1} -90 8"], "bird_side_black"),
    ("inventory", [f"/give @s chocobosreborn:gysahl_green 8", "/give @s chocobosreborn:carob_nut 2",
                   "/give @s chocobosreborn:chocobo_lure", "/give @s chocobosreborn:chocobo_saddle",
                   "/give @s chocobosreborn:yellow_chocobo_spawn_egg", "/give @s chocobosreborn:square_gate",
                   "/give @s chocobosreborn:gp 32", "/give @s chocobosreborn:zeio_nut"], None),
    ("hotbar", [f"/tp @s {X + 9} {Y + 3} {PEN_Z + 4} 180 12"], "hotbar"),
    ("riding", [f"/summon chocobosreborn:chocobo {X + 9} {Y + 2} {PEN_Z + 9} {{Plumage:5,Saddled:1b,Rotation:[180f,0f]}}",
                f"/tp @s {X + 9} {Y + 2} {PEN_Z + 9} 180 0",
                "/ride @s mount @e[type=chocobosreborn:chocobo,limit=1,sort=nearest]", "F5", "F5"], "riding"),
    ("square", ["F5", "/ride @s dismount", "/chocobosreborn square build c_meadow",
                "/execute in chocobosreborn:square run tp @s 0.5 70 -40.5 180 18"], "square_paddock"),
    ("square_course", ["/execute in chocobosreborn:square run tp @s 0.5 84 30.5 180 32"], "square_course"),
    ("square_keeper", ["/execute in chocobosreborn:square run tp @s -13.5 66 -63.5 -90 8"], "square_keeper"),
    ("square_arch", ["/execute in chocobosreborn:square run tp @s 0.5 66 -62.5 0 -6"], "square_arch"),
    ("square_bookie", ["/execute in chocobosreborn:square run tp @s 0.5 66 -65.5 180 4"], "square_bookie"),
    ("square_air", ["/execute in chocobosreborn:square run setblock 0 91 -30 glass",
                    "/execute in chocobosreborn:square run tp @s 0.5 92 -29.5 180 50"], "square_air"),
    ]


def chat_lines(since):
    return [l for l in LOG.read_text(errors="replace").splitlines()[since:] if "[CHAT]" in l]


def locate_farm():
    """/locate the farm, force-load it, read Esther's Pos from chat, set X/Y/Z."""
    global X, Y, Z, FARM
    n0 = len(LOG.read_text(errors="replace").splitlines())
    chat("/locate structure chocobosreborn:chocobo_farm", settle=3)
    m = None
    for _ in range(10):
        for l in chat_lines(n0):
            m = re.search(r"is at \[(-?\d+), ~, (-?\d+)\]", l)
            if m:
                break
        if m:
            break
        time.sleep(2)
    if not m:
        print("farm not located; keeping defaults")
        return False
    lx, lz = int(m.group(1)), int(m.group(2))
    chat(f"/forceload add {lx - 48} {lz - 48} {lx + 48} {lz + 48}", settle=2)
    chat(f"/tp @s {lx} 120 {lz}", settle=45)   # far teleport -> "Loading terrain" screen; keys are lost until it clears
    pm = None
    for attempt in range(8):
        n1 = len(LOG.read_text(errors="replace").splitlines())
        role = 6 if attempt % 2 == 0 else 0   # Farmhand (current farms) or Esther (farms placed by older builds)
        chat(f"/execute positioned {lx} 70 {lz} run data get entity @e[type=chocobosreborn:kin_steward,nbt={{Role:{role}}},limit=1,sort=nearest,distance=..64] Pos", settle=6)
        for l in chat_lines(n1):
            pm = re.search(r"\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]", l)
            if pm:
                break
        if pm:
            break
    if not pm:
        print("Esther not found near the farm; keeping defaults")
        return False
    ex, ey, ez = (float(pm.group(i)) for i in (1, 2, 3))
    X, Y, Z = int(round(ex - 15.5)), int(round(ey - 2)), int(round(ez - 12.5))
    FARM = (X, Y, Z)
    print("farm origin", FARM)
    return True



def sh(*cmd, check=True, **kw):
    return subprocess.run(cmd, check=check, capture_output=True, text=True, **kw)


def xdo(*args):
    sh("xdotool", *args, env={**os.environ, "DISPLAY": DISPLAY})


def shot(name):
    SHOTS.mkdir(parents=True, exist_ok=True)
    out = SHOTS / f"{name}.png"
    sh("import", "-display", DISPLAY, "-window", "root", str(out))
    print("shot", out)
    return out


def chat(cmd, settle=1.5):
    xdo("key", "--clearmodifiers", "t")
    time.sleep(0.6)
    xdo("type", "--delay", "12", cmd)
    time.sleep(0.3)
    xdo("key", "Return")
    time.sleep(settle)


def wait_for(pattern, timeout, since=0):
    t0 = time.time()
    rx = re.compile(pattern)
    while time.time() - t0 < timeout:
        if LOG.exists():
            text = LOG.read_text(errors="replace")[since:]
            if rx.search(text):
                return True
        time.sleep(3)
    return False


def main():
    keep = "--keep" in sys.argv
    env = {**os.environ, "DISPLAY": DISPLAY, "LIBGL_ALWAYS_SOFTWARE": "1", "GALLIUM_DRIVER": "llvmpipe",
           "MESA_GL_VERSION_OVERRIDE": "4.5"}
    xvfb = subprocess.Popen(["Xvfb", DISPLAY, "-screen", "0", f"{W}x{H}x24", "-nolisten", "tcp"],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2)
    LOG.write_text("")
    # custom dimension = "experimental settings" dialog on first load; pre-confirm it
    try:
        import nbtlib
        lvl = nbtlib.load(str(ROOT / "run/saves/harness/level.dat"))
        lvl["Data"]["confirmedExperimentalSettings"] = nbtlib.Byte(1)
        lvl["Data"]["allowCommands"] = nbtlib.Byte(1)   # a server world has cheats off; chat commands need them
        lvl["Data"]["GameType"] = nbtlib.Int(1)          # creative: nothing can kill the harness player
        lvl["Data"]["Difficulty"] = nbtlib.Byte(0)
        lvl["Data"]["SpawnY"] = nbtlib.Int(120)  # spawn in the air (creative), never inside a hill
        for f in (ROOT / "run/saves/harness/playerdata").glob("*"):
            f.unlink()  # fresh player -> world game type + world spawn
        lvl.save()
    except Exception as e:  # noqa: BLE001
        print("level.dat not patched:", e)
    # first-run gates: skip the accessibility onboarding, keep rendering unfocused, no tutorial toasts
    opts = ROOT / "run/options.txt"
    lines = {}
    if opts.exists():
        for l in opts.read_text().splitlines():
            if ":" in l:
                k, v = l.split(":", 1)
                lines[k] = v
    lines.update({"onboardAccessibility": "false", "pauseOnLostFocus": "false", "tutorialStep": "none",
                  "skipMultiplayerWarning": "true", "joinedFirstServer": "true", "renderDistance": "8",
                  "guiScale": "2", "fov": "0.0", "soundCategory_master": "0.0"})
    opts.write_text("\n".join(f"{k}:{v}" for k, v in lines.items()) + "\n")
    with LOG.open("a") as logf:
        client = subprocess.Popen([str(ROOT / "gradlew"), "runVisual"], cwd=ROOT, env=env, stdout=logf,
                                  stderr=subprocess.STDOUT)
        try:
            # singleplayer: the integrated server logs the join
            if not wait_for(r"joined the game|Preparing spawn area: 100%", 900):
                print("client did not reach the world; see", LOG)
                return 1
            time.sleep(25)  # let chunks + entities render in
            wid = sh("xdotool", "search", "--sync", "--name", "Minecraft", env={**os.environ, "DISPLAY": DISPLAY}).stdout.split()[0]
            sh("xdotool", "windowraise", wid, env={**os.environ, "DISPLAY": DISPLAY}, check=False)
            sh("xdotool", "windowfocus", "--sync", wid, env={**os.environ, "DISPLAY": DISPLAY}, check=False)
            sh("xdotool", "mousemove", str(W // 2), str(H // 2), env={**os.environ, "DISPLAY": DISPLAY}, check=False)
            time.sleep(1)
            locate_farm()
            for name, cmds, shotname in scenes():
                for c in cmds:
                    if c.startswith("/"):
                        chat(c, settle=1.2)
                    else:
                        xdo("key", c)   # a bare key press, e.g. F5 = cycle camera
                        time.sleep(0.8)
                if shotname:
                    time.sleep(20)  # llvmpipe: chunks + a few frames
                    shot(shotname)
            # asset warnings the client logged (missing models / textures / sounds)
            warns = [l for l in LOG.read_text(errors="replace").splitlines()
                     if "chocobosreborn" in l and ("Unable to load" in l or "missing model" in l
                                                or "Missing sound" in l or "Failed to load" in l)]
            (SHOTS / "asset_warnings.txt").write_text("\n".join(warns) + "\n")
            print(len(warns), "asset warnings ->", SHOTS / "asset_warnings.txt")
            if keep:
                print("--keep: client left running")
                client.wait()
        finally:
            if not keep:
                client.terminate()
                try:
                    client.wait(20)
                except subprocess.TimeoutExpired:
                    client.kill()
                xvfb.terminate()
    return 0


if __name__ == "__main__":
    sys.exit(main())
