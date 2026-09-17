"""Draw the boost pad's animated chevron texture (16x16, four scrolling frames):

    python tools/draw_boost_pad.py
"""
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
