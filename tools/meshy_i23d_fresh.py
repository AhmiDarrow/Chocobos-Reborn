"""Fresh start (Ahmi, 2026-09-16 evening): Meshy image-to-3D from the approved
still-8 (art/ref/gen/still8.jpg, our own render) and use THAT mesh + THAT texture
as the bird, with no block remesh, no re-bake and no repaint in between.

Writes art/meshy/fresh/<tag>.glb, <tag>_task.json and the thumbnail. Key file
$MESHY_KEY_FILE (default ~/.meshy_key), never printed.

    python tools/meshy_i23d_fresh.py [--tag fresh] [--image art/ref/gen/still8.jpg] [--poly 30000] [--no-remesh]
    python tools/meshy_i23d_fresh.py --tag saddled --image art/ref/gen/still8_saddled.jpg
"""
from __future__ import annotations

import base64
import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
IMG = ROOT / "art/ref/gen/still8.jpg"
OUT = ROOT / "art/meshy/fresh"
KEY = Path(os.environ.get("MESHY_KEY_FILE", Path.home() / ".meshy_key")).read_text().strip()
API = "https://api.meshy.ai/openapi/v1/image-to-3d"
CTX = ssl.create_default_context()


def arg(name, default):
    if name in sys.argv:
        return type(default)(sys.argv[sys.argv.index(name) + 1])
    return default


def req(method, url, body=None, timeout=90):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Authorization": f"Bearer {KEY}", "Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=timeout, context=CTX) as resp:
            raw = resp.read()
            return resp.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        print("FAIL", e.code, e.read().decode("utf-8", "replace")[:600])
        raise


def download(url, dest: Path):
    r = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(r, timeout=300, context=CTX) as resp:
        dest.write_bytes(resp.read())
    print("wrote", dest, dest.stat().st_size)


def main():
    global IMG
    tag = arg("--tag", "fresh")
    poly = arg("--poly", 30000)
    IMG = ROOT / arg("--image", "art/ref/gen/still8.jpg")
    OUT.mkdir(parents=True, exist_ok=True)
    b64 = base64.b64encode(IMG.read_bytes()).decode("ascii")
    payload = {
        "image_url": "data:image/jpeg;base64," + b64,
        "ai_model": "latest",
        "should_texture": True,
        "enable_pbr": False,
        "image_enhancement": False,
        "should_remesh": "--no-remesh" not in sys.argv,
        "topology": "triangle",
        "target_polycount": poly,
        "symmetry_mode": "auto",
        "texture_prompt": "clean flat colours exactly as the image: even yellow plumage, orange bill, "
                          "white eye with blue iris and black pupil, brown legs, white claws, "
                          "dark brown leather saddle and bridle straps, red saddle seat, grey buckles",
    }
    print("posting image-to-3d", IMG.name, "poly", poly, "remesh", payload["should_remesh"])
    _, created = req("POST", API, payload)
    tid = created.get("result") or created.get("id")
    print("task", tid)
    (OUT / f"{tag}_task_id.txt").write_text(tid, encoding="utf-8")
    task = {}
    for _ in range(240):
        _, task = req("GET", f"{API}/{tid}")
        st = task.get("status")
        print(st, task.get("progress"), flush=True)
        if st in ("SUCCEEDED", "FAILED", "CANCELED"):
            break
        time.sleep(10)
    (OUT / f"{tag}_task.json").write_text(json.dumps(task, indent=1), encoding="utf-8")
    if task.get("status") != "SUCCEEDED":
        print("failed", task.get("task_error"))
        raise SystemExit(1)
    mu = task.get("model_urls") or {}
    if mu.get("glb"):
        download(mu["glb"], OUT / f"{tag}.glb")
    if task.get("thumbnail_url"):
        download(task["thumbnail_url"], OUT / f"{tag}_thumb.png")
    for i, t in enumerate(task.get("texture_urls") or []):
        if t.get("base_color"):
            download(t["base_color"], OUT / f"{tag}_base_color_{i}.png")
    print("done")


if __name__ == "__main__":
    main()
