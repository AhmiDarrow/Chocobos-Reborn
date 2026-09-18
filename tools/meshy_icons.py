"""Generate item / crop icons with Meshy text-to-image, then key the
background to alpha and downsample to clean 32x32 pixel art.

Key file: $MESHY_KEY_FILE (default ~/.meshy_key). Never printed, never
written into the repo.

    python tools/meshy_icons.py test            # one icon, to eyeball
    python tools/meshy_icons.py all             # every icon in ICONS
    python tools/meshy_icons.py only gysahl_green pepio_nut
Raw renders land in art/icons/raw/, finished PNGs in textures/item|block.
"""
from __future__ import annotations

import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/chocobosreborn"
RAW = ROOT / "art/icons/raw"
KEY = Path(os.environ.get("MESHY_KEY_FILE", Path.home() / ".meshy_key")).read_text().strip()
CTX = ssl.create_default_context()
API = "https://api.meshy.ai/openapi/v1/text-to-image"
# Vanilla Minecraft is 16x16; keep the mod cohesive with it.
SIZE = 16

STYLE = ("Clean 32x32 pixel-art game item icon, crisp square pixels, bold dark outline, "
         "flat cel shading with two-tone highlights, centred, filling most of the frame, "
         "on a perfectly flat solid pure {key} background, no shadow, no text, no border.")
KEYS = {"magenta": "magenta (#FF00FF)", "green": "lime green (#00FF00)"}
# icons whose own colour would fight the magenta key
KEY_FOR = {"chocobo_lure": "green", "pram_nut": "green"}

GREEN_PROMPT = "a small bunch of {look} leafy vegetable greens tied at the stem, {colour} leaves"
NUT_PROMPT = "a single round {look} nut with a small cap, {colour} shell"

ICONS = {
    # item icons
    "gysahl_green": GREEN_PROMPT.format(look="fresh spring", colour="bright green"),
    "krakka_green": GREEN_PROMPT.format(look="crisp", colour="blue-green"),
    "tantal_green": GREEN_PROMPT.format(look="wiry", colour="yellow-green"),
    "pahsana_green": GREEN_PROMPT.format(look="soft broad", colour="purple-tinted"),
    "curiel_green": GREEN_PROMPT.format(look="curly", colour="teal"),
    "mimett_green": GREEN_PROMPT.format(look="lush", colour="vivid emerald"),
    "reagan_green": GREEN_PROMPT.format(look="ruffled", colour="orange-gold"),
    "sylkis_green": GREEN_PROMPT.format(look="silky glowing", colour="mint green with golden edges"),
    "pepio_nut": NUT_PROMPT.format(look="plain", colour="tan"),
    "luchile_nut": NUT_PROMPT.format(look="smooth", colour="olive"),
    "saraha_nut": NUT_PROMPT.format(look="glossy", colour="terracotta red"),
    "lasan_nut": NUT_PROMPT.format(look="matte", colour="sage green"),
    "pram_nut": NUT_PROMPT.format(look="polished", colour="lavender"),
    "porov_nut": NUT_PROMPT.format(look="ridged", colour="slate blue-grey"),
    "carob_nut": NUT_PROMPT.format(look="dark glossy", colour="deep chocolate brown"),
    "zeio_nut": NUT_PROMPT.format(look="radiant magical", colour="shining gold with a soft glow"),
    "chocobo_lure": "a glowing purple crystal materia orb on a short leather cord",
    "gp": "a round gold coin embossed with a three-toed bird footprint",
    "chocobo_saddle": "a brown leather riding saddle with a small silver buckle, side view",
    "gysahl_green_seeds": "a small pile of five pale green teardrop seeds",
}

CROP_STAGES = {
    # block textures, drawn as a front-on sprite of the plant against magenta
    "gysahl_green0": "a tiny green seedling sprout with two leaves, just emerging from dark soil, pixel art crop stage",
    "gysahl_green1": "a small young leafy green plant with four leaves, pixel art crop stage",
    "gysahl_green2": "a medium leafy green plant with a thick rosette of leaves, pixel art crop stage",
    "gysahl_green3": "a large lush leafy green plant with many leaves and a pale bulb at the base, pixel art crop stage",
    "gysahl_green4": "a fully grown leafy green plant with a tall bunch of ripe bright green leaves and a large pale bulb, pixel art crop stage",
}


TILES = {
    "square_gate": "seamless 32x32 pixel-art block texture tile of an ornate gold and dark iron race gate panel with a small green gem in the centre, top-down flat lighting",
    "square_gate_short": "seamless 32x32 pixel-art block texture tile of an ornate gold and dark iron race gate panel with a small yellow gem in the centre, top-down flat lighting",
    "square_gate_long": "seamless 32x32 pixel-art block texture tile of an ornate gold and dark iron race gate panel with a small blue gem in the centre, top-down flat lighting",
    "square_gate_return": "seamless 32x32 pixel-art block texture tile of an ornate gold and dark iron race gate panel with a small red gem in the centre, top-down flat lighting",
}
TILE_STYLE = "Crisp square pixels, no text, no border, fills the whole frame edge to edge."


def tile(src: Path, size: int) -> Image.Image:
    im = Image.open(src).convert("RGB").resize((size, size), Image.Resampling.BOX)
    q = im.quantize(colors=16, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGB")
    return q.convert("RGBA")


def req(method, url, body=None):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Authorization": f"Bearer {KEY}", "Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=60, context=CTX) as resp:
            raw = resp.read()
            return resp.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        print("FAIL", e.code, url, e.read().decode("utf-8", "replace")[:300])
        raise


def download(url, dest: Path):
    r = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(r, timeout=120, context=CTX) as resp:
        dest.write_bytes(resp.read())


def generate(name: str, prompt: str) -> Path:
    RAW.mkdir(parents=True, exist_ok=True)
    dest = RAW / f"{name}.png"
    if dest.is_file():
        return dest
    key = KEY_FOR.get(name, "magenta")
    style = STYLE.format(key=KEYS[key])
    _, data = req("POST", API, {"ai_model": "nano-banana-2", "prompt": prompt + " " + style, "aspect_ratio": "1:1"})
    tid = data.get("result") or data.get("id")
    url = f"{API}/{tid}"
    task = {}
    for _ in range(80):
        _, task = req("GET", url)
        st = task.get("status")
        if st in ("SUCCEEDED", "FAILED", "CANCELED"):
            break
        time.sleep(3)
    if task.get("status") != "SUCCEEDED":
        raise RuntimeError(f"{name}: {task.get('status')} {task.get('task_error')}")
    download((task.get("image_urls") or [None])[0], dest)
    print("generated", name)
    return dest


def key_and_shrink(src: Path, size: int, anchor: str = "center", key: str = "magenta") -> Image.Image:
    """Key colour -> alpha, tight crop, downsample to `size` with box filter, then re-quantise."""
    im = np.asarray(Image.open(src).convert("RGB"), dtype=np.float32) / 255.0
    r, g, b = im[..., 0], im[..., 1], im[..., 2]
    if key == "green":
        mag = (g > 0.7) & (r < 0.45) & (b < 0.45)
    else:
        # magenta-ness: high r and b, low g
        mag = (r > 0.6) & (b > 0.6) & (g < 0.45) & (np.abs(r - b) < 0.35)
    alpha = (~mag).astype(np.float32)
    # soften edge: pixels adjacent to magenta get partial alpha via 3x3 mean
    a = alpha.copy()
    pad = np.pad(alpha, 1, mode="edge")
    acc = np.zeros_like(alpha)
    for dy in range(3):
        for dx in range(3):
            acc += pad[dy:dy + alpha.shape[0], dx:dx + alpha.shape[1]]
    edge = (acc < 9) & (alpha > 0)
    a[edge] = 0.75
    rgba = np.dstack([im, a])
    ys, xs = np.nonzero(a > 0.5)
    if ys.size == 0:
        raise RuntimeError(f"{src.name}: nothing left after keying")
    y0, y1, x0, x1 = ys.min(), ys.max(), xs.min(), xs.max()
    h, w = y1 - y0 + 1, x1 - x0 + 1
    side = int(max(h, w) * 1.02)
    cy, cx = (y0 + y1) // 2, (x0 + x1) // 2
    canvas = np.zeros((side, side, 4), dtype=np.float32)
    sy0, sx0 = max(0, cy - side // 2), max(0, cx - side // 2)
    if anchor == "bottom":
        # crops stand on the soil: bottom of the sprite = bottom of the tile
        sy0 = max(0, y1 + 1 - side)
    sub = rgba[sy0:sy0 + side, sx0:sx0 + side]
    canvas[:sub.shape[0], :sub.shape[1]] = sub
    pil = Image.fromarray((canvas * 255).astype(np.uint8), "RGBA")
    # premultiplied box downsample avoids magenta halos, then hard alpha
    small = pil.resize((size, size), Image.Resampling.BOX)
    arr = np.asarray(small).astype(np.float32)
    al = arr[..., 3:4] / 255.0
    rgb = np.where(al > 0.02, arr[..., :3] / np.maximum(al, 1e-3), 0)
    rgb = np.clip(rgb, 0, 255)
    hard = (al[..., 0] > 0.45).astype(np.uint8) * 255
    out = np.dstack([rgb.astype(np.uint8), hard]).astype(np.uint8)
    return vanillaise(Image.fromarray(out, "RGBA"))


def vanillaise(img: Image.Image, colours: int = 14) -> Image.Image:
    """Minecraft-cohesive: a small palette (vanilla items use ~6-14 colours) and a
    1px darker outline where the sprite meets alpha."""
    arr = np.asarray(img).copy()
    a = arr[..., 3] > 0
    if not a.any():
        return img
    rgb = Image.fromarray(arr[..., :3], "RGB")
    q = rgb.quantize(colors=colours, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGB")
    out = np.asarray(q).copy()
    # outline: opaque pixels touching transparent ones get darkened
    pad = np.pad(a, 1)
    edge = a & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
    out[edge] = (out[edge].astype(np.float32) * 0.55).astype(np.uint8)
    return Image.fromarray(np.dstack([out, arr[..., 3]]), "RGBA")


def write_item(name: str, img: Image.Image):
    d = ASSETS / "textures/item"
    d.mkdir(parents=True, exist_ok=True)
    img.save(d / f"{name}.png")
    m = ASSETS / "models/item"
    m.mkdir(parents=True, exist_ok=True)
    (m / f"{name}.json").write_text(json.dumps({"parent": "minecraft:item/generated",
                                               "textures": {"layer0": f"chocobosreborn:item/{name}"}}, indent=2) + "\n")


def write_block(name: str, img: Image.Image):
    d = ASSETS / "textures/block"
    d.mkdir(parents=True, exist_ok=True)
    img.save(d / f"{name}.png")


def run(names):
    # Submit up to 4 tasks at once, then finish each as it lands.
    from concurrent.futures import ThreadPoolExecutor
    pending = [n for n in names if not (RAW / f"{n}.png").is_file()]

    def gen(n):
        prompt = ICONS.get(n) or CROP_STAGES.get(n)
        if prompt:
            return generate(n, prompt)
        return None

    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(gen, [n for n in pending if n in ICONS or n in CROP_STAGES]))
    for n in names:
        if n in ICONS:
            write_item(n, key_and_shrink(generate(n, ICONS[n]), SIZE, key=KEY_FOR.get(n, "magenta")))
        elif n in TILES:
            RAW.mkdir(parents=True, exist_ok=True)
            dest = RAW / f"{n}.png"
            if not dest.is_file():
                _, data = req("POST", API, {"ai_model": "nano-banana-2", "prompt": TILES[n] + " " + TILE_STYLE, "aspect_ratio": "1:1"})
                tid = data.get("result") or data.get("id")
                task = {}
                for _ in range(80):
                    _, task = req("GET", f"{API}/{tid}")
                    if task.get("status") in ("SUCCEEDED", "FAILED", "CANCELED"):
                        break
                    time.sleep(3)
                download((task.get("image_urls") or [None])[0], dest)
                print("generated", n)
            write_block(n, tile(dest, SIZE))
        elif n in CROP_STAGES:
            write_block(n, key_and_shrink(generate(n, CROP_STAGES[n]), SIZE, anchor="bottom"))
        else:
            print("unknown icon", n)


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "test"
    if mode == "test":
        run(["gysahl_green"])
    elif mode == "all":
        # square_gate* tiles are hand-painted in tools/paint_item_art.py
        run(list(ICONS) + list(CROP_STAGES))
    elif mode == "only":
        run(sys.argv[2:])
