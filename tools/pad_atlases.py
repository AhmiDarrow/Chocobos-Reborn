"""Edge-pad the baked breed atlases: every background texel between the UV
islands takes the colour of the nearest island texel, so sampling at island
borders (and any mip/bilinear filtering) never picks up the black gaps that
made the in-game bird look speckled. fresh_ship.py calls this after writing the atlases.

    python tools/pad_atlases.py
"""
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[1]
TEXDIR = ROOT / "art/atlases/chocobo"   # the 2048 masters (ship_atlases.py makes the jar copies)


def pad(path: Path):
    im = np.array(Image.open(path).convert("RGBA")).astype(np.uint8)
    rgb = im[..., :3].astype(int)
    # gaps are the bake's cleared black (0,0,0); the pupils are painted an off-black
    # (true black is sum 0; the threshold stays under any painted dark pixel)
    background = (rgb.sum(axis=2) < 24) | (im[..., 3] < 8)
    if not background.any():
        return 0
    _, (iy, ix) = ndimage.distance_transform_edt(background, return_indices=True)
    out = im.copy()
    out[background] = im[iy[background], ix[background]]
    out[..., 3] = 255
    Image.fromarray(out, "RGBA").save(path)
    return int(background.sum())


def main(folder="chocobo"):
    for p in sorted((TEXDIR.parent / folder).glob("*.png")):
        n = pad(p)
        print(f"{p.name}: padded {n} texels")


if __name__ == "__main__":
    main()
