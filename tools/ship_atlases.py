"""Ship the breed atlases: Lanczos the 2048 masters in art/atlases/<variant>/ down to
1024 and write them as RGB (the bake's alpha is all 255, so the channel only cost
jar space; the cutout shader treats an RGB texture as opaque). Only yellow, purple and
flame ship: the five solid breeds are recoloured from yellow on the client at load
(client/DerivedAtlasTexture, the same rule as paint_albedo.recolor_plumage), so their
masters stay here for previews. Stale solid-breed files in the jar folder are removed.

    python tools/ship_atlases.py                # every variant with masters
    python tools/ship_atlases.py chocobo        # one variant

Run after fresh_ship.py / repaint_atlases.py, which write the masters.
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
MASTERS = ROOT / "art/atlases"
TEXDIR = ROOT / "src/main/resources/assets/chocobosreborn/textures/entity"
SIZE = 1024
SHIPPED = ("yellow", "purple", "flame")


def ship(variant: str) -> int:
    src = MASTERS / variant
    dst = TEXDIR / variant
    dst.mkdir(parents=True, exist_ok=True)
    total = 0
    for stale in dst.glob("*.png"):
        if stale.stem not in SHIPPED:
            stale.unlink()
            print(f"{variant}/{stale.name}: removed (derived on the client)")
    for master in sorted(src.glob("*.png")):
        if master.stem not in SHIPPED:
            continue
        im = Image.open(master).convert("RGBA")
        if im.getchannel("A").getextrema() != (255, 255):
            raise SystemExit(f"{master}: atlas has real alpha; ship_atlases assumes opaque bakes")
        out = im.convert("RGB")
        if out.size != (SIZE, SIZE):
            out = out.resize((SIZE, SIZE), Image.LANCZOS)
        target = dst / master.name
        out.save(target, "PNG", optimize=True, compress_level=9)
        size = target.stat().st_size
        total += size
        print(f"{variant}/{master.name}: {size // 1024} KiB")
    return total


def main(argv):
    variants = argv or sorted(p.name for p in MASTERS.iterdir() if p.is_dir())
    total = sum(ship(v) for v in variants)
    print(f"shipped {len(variants)} variant(s), {total // 1024} KiB")


if __name__ == "__main__":
    main(sys.argv[1:])
