"""Re-derive every breed atlas from the shipped yellow atlas, no Blender needed.

fresh_ship.py writes <variant>/yellow.png straight from the Meshy albedo; the other
breeds are paint_albedo.paint_breed() over it. Run this after changing the palette,
the plumage guard or the End / Nether textures:

    python tools/repaint_atlases.py            # all variants
    python tools/repaint_atlases.py chocobo    # one variant folder
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pad_atlases  # noqa: E402
from paint_albedo import PLUMAGE, paint_breed  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
TEXDIR = ROOT / "src/main/resources/assets/chocobosreborn/textures/entity"
VARIANTS = ["chocobo", "chocobo_saddled", "chocobo_armor_iron", "chocobo_armor_diamond"]


def repaint(variant: str):
    folder = TEXDIR / variant
    src = folder / "yellow.png"
    if not src.is_file():
        print("skip", variant, "(no yellow.png)")
        return
    px = np.asarray(Image.open(src).convert("RGBA")).astype(np.float32) / 255.0
    for name in PLUMAGE:
        if name == "yellow":
            continue
        out = paint_breed(px, name, variant)
        Image.fromarray((np.clip(out, 0, 1) * 255.0 + 0.5).astype(np.uint8), "RGBA").save(folder / f"{name}.png")
    pad_atlases.main(variant)
    print("repainted", variant)


if __name__ == "__main__":
    for v in (sys.argv[1:] or VARIANTS):
        repaint(v)
