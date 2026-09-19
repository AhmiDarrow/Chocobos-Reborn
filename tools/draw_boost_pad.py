"""SUPERSEDED (2026-09-19): tools/pixel_items.py is the source of truth for these textures
(32x32, Ninjacat Skies family style). Running this would overwrite them with the old art,
so it refuses unless given --legacy.

Draw the boost pad's animated chevron texture (16x16, four scrolling frames):

    python tools/draw_boost_pad.py
"""
import sys as _sys
if "--legacy" not in _sys.argv:
    raise SystemExit("superseded by tools/pixel_items.py; pass --legacy to run anyway")
_sys.argv = [a for a in _sys.argv if a != "--legacy"]

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/chocobosreborn/textures/block/boost_pad.png"
FRAMES = 4
BASE, EDGE = (22, 62, 150, 255), (48, 100, 200, 255)
CHEV, CHEV2 = (252, 214, 60, 255), (255, 240, 150, 255)

im = Image.new("RGBA", (16, 16 * FRAMES))
px = im.load()
for f in range(FRAMES):
    for y in range(16):
        for x in range(16):
            c = EDGE if x in (0, 15) or y in (0, 15) else BASE
            yy = (y + f * 2) % 8          # chevrons point north and scroll north
            k = abs(x - 7.5) - 0.5
            if 0 <= k < 8 and (int(k) == yy or int(k) == yy - 1):
                c = CHEV if int(k) == yy else CHEV2
            px[x, y + 16 * f] = c
im.save(OUT)
Path(str(OUT) + ".mcmeta").write_text(json.dumps({"animation": {"frametime": 2}}), encoding="utf-8")
print("wrote", OUT)
