"""Breed palette + plumage recolour for the chocobo atlases.

recolor_plumage(px, rgb) shifts only texels that ChocoboMeshRenderer.isPlumage
would treat as plumage (r>160, g>120, b<120, r-g<90), luminance-preserving, so
beak / eyes / legs / claws stay. PLUMAGE is the nine breed colours (sRGB 0-255).
"""
from __future__ import annotations

from pathlib import Path

import numpy as np

YELLOW = np.array([245, 184, 18], dtype=np.float32) / 255.0
ORANGE = np.array([0.90, 0.40, 0.06], dtype=np.float32)
BROWN = np.array([0.18, 0.08, 0.035], dtype=np.float32)
WHITE = np.array([0.93, 0.93, 0.95], dtype=np.float32)
BLUE = np.array([0.16, 0.38, 0.78], dtype=np.float32)
BLACK = np.array([0.04, 0.03, 0.03], dtype=np.float32)

PLUMAGE = {
    "yellow": (245, 184, 18),
    "green": (76, 176, 90),
    "blue": (58, 143, 208),
    "white": (232, 228, 220),
    "black": (44, 42, 50),
    "gold": (232, 164, 22),   # deep warm gold (Ahmi: "gold needs to look more gold"); was pale #FFD24A
    "purple": (146, 84, 214),
    "flame": (190, 42, 30),    # Nether Chocobo: red, lava accents added by accent_plumage
}


def _luma(rgb):
    return 0.2126 * rgb[..., 0] + 0.7152 * rgb[..., 1] + 0.0722 * rgb[..., 2]


def plumage_mask(px, variant="chocobo"):
    """isPlumage texels (beak, eyes, legs, tack and armour plate stay). A gold
    armour mesh was tried and dropped: its plating is yellow too and bled the
    breed colour, so only iron and diamond have meshes."""
    r, g, b = px[:, :, 0], px[:, :, 1], px[:, :, 2]
    ri = (r * 255.0).astype(np.int32)
    gi = (g * 255.0).astype(np.int32)
    bi = (b * 255.0).astype(np.int32)
    plum = (ri > 160) & (gi > 120) & (bi < 120) & ((ri - gi) < 60)   # beak orange has r-g >= 70
    return plum


def recolor_plumage(px, rgb, variant="chocobo"):
    """Shift only isPlumage texels. Beak, legs, eyes (and gold plating) stay."""
    out = px.copy()
    plum = plumage_mask(px, variant)
    lum = _luma(out[..., :3])
    ylum = float(_luma(YELLOW.reshape(1, 1, 3))[0, 0])
    scale = np.clip(lum / max(ylum, 0.05), 0.50, 1.45)
    tr, tg, tb = rgb[0] / 255.0, rgb[1] / 255.0, rgb[2] / 255.0
    out[plum, 0] = np.clip(tr * scale[plum], 0.0, 1.0)
    out[plum, 1] = np.clip(tg * scale[plum], 0.0, 1.0)
    out[plum, 2] = np.clip(tb * scale[plum], 0.0, 1.0)
    print("recolor", rgb, "plum texels", int(plum.sum()))
    return out


# Accents painted over the recoloured plumage for the two dimension birds:
# (bright colour, dark colour, bright share, dark share). Patches are hashed
# 9x9-texel cells, bright ones only on the lighter feathers and dark ones only in
# the shadowed plumage, so they read as lava seams / stone flecks on the model
# rather than confetti.
ACCENTS = {}   # purple / flame are textured now (TEXTURED); kept for one-off experiments
ACCENT_CELL = 9


def accent_plumage(px, name, seed=7):
    """Sprinkle a colour's accents over its plumage texels (after recolor_plumage)."""
    acc = ACCENTS.get(name)
    if acc is None:
        return px
    bright, dark, p_bright, p_dark = acc
    out = px.copy()
    lum = _luma(out[..., :3])
    tgt = np.array(PLUMAGE[name], dtype=np.float32) / 255.0
    ylum = float(_luma(tgt.reshape(1, 1, 3))[0, 0]) or 0.05
    scale = np.clip(lum / ylum, 0.50, 1.45)[..., None]
    dist = np.abs(out[..., :3] - tgt.reshape(1, 1, 3) * scale).sum(axis=2)
    plum = dist < 0.12
    if not plum.any():
        return out
    med = float(np.median(lum[plum]))
    h, w = plum.shape
    yy, xx = np.mgrid[0:h, 0:w]
    cy, cx = yy // ACCENT_CELL, xx // ACCENT_CELL
    n = ((cx * 73856093) ^ (cy * 19349663) ^ (seed * 83492791)) & 0xFFFF
    noise = n / 65535.0
    # a second, finer hash breaks each patch's edge so it is not a square
    n2 = (((xx // 3) * 2654435761) ^ ((yy // 3) * 97 * seed)) & 0xFF
    edge = (n2 / 255.0) < 0.75
    bmask = plum & (noise < p_bright) & (lum >= med) & edge
    dmask = plum & (noise > 1.0 - p_dark) & (lum < med) & edge
    for mask, col in ((bmask, bright), (dmask, dark)):
        c = np.array(col, dtype=np.float32) / 255.0
        out[mask, 0] = np.clip(c[0] * scale[mask, 0], 0, 1)
        out[mask, 1] = np.clip(c[1] * scale[mask, 0], 0, 1)
        out[mask, 2] = np.clip(c[2] * scale[mask, 0], 0, 1)
    print("accent", name, "bright", int(bmask.sum()), "dark", int(dmask.sum()), "of plumage", int(plum.sum()))
    return out


# End / Nether plumage: the feathers take the vanilla end stone / lava texture,
# shaded by the feather luminance and pulled a little toward the breed colour so
# the bird still reads as purple / red at a distance (Ahmi: "end chocobo should
# have plumage of endstone texture, same for the nether but with lava texture").
TEXTURED = {
    "purple": ("end_stone.png", (146, 84, 214), 0.55),
    "flame": ("lava_still.png", (190, 42, 30), 0.30),
}
TEXTURE_SCALE = 4   # 16-texel vanilla tile drawn at 64 atlas texels
VANILLA_DIR = Path(__file__).resolve().parents[1] / "art/ref/vanilla"


def texture_plumage(px, name, variant="chocobo"):
    """Tile the breed's vanilla texture over its plumage texels (after recolor_plumage)."""
    spec = TEXTURED.get(name)
    if spec is None:
        return px
    from PIL import Image
    fname, tint, tint_share = spec
    tex = np.asarray(Image.open(VANILLA_DIR / fname).convert("RGB")).astype(np.float32) / 255.0
    tex = tex[:16, :16]                       # first frame of an animation strip
    tex = np.repeat(np.repeat(tex, TEXTURE_SCALE, axis=0), TEXTURE_SCALE, axis=1)
    h, w = px.shape[:2]
    reps = (h // tex.shape[0] + 1, w // tex.shape[1] + 1, 1)
    tiled = np.tile(tex, reps)[:h, :w]
    out = px.copy()
    plum = plumage_mask(px, variant) | _near_breed(px, name)
    # only the show feathers (tail fan, neck ruff, head crest) take the texture;
    # the body stays the breed colour (art/masks, baked by bake_feather_mask.py)
    mask_path = Path(__file__).resolve().parents[1] / "art/masks" / f"{variant}_feathers.png"
    if mask_path.is_file():
        m = np.asarray(Image.open(mask_path).convert("L")) > 127
        if m.shape == plum.shape:
            plum &= m
        else:
            print("feather mask size mismatch", m.shape, plum.shape)
    lum = _luma(px[..., :3])
    tgt = np.array(PLUMAGE[name], dtype=np.float32) / 255.0
    ylum = float(_luma(tgt.reshape(1, 1, 3))[0, 0]) or 0.05
    # shading relative to the yellow source (the recoloured texels keep its luma ratio)
    shade = np.clip(lum / max(ylum, 0.05), 0.55, 1.35)[..., None]
    col = tiled * (1.0 - tint_share) + (np.array(tint, dtype=np.float32) / 255.0) * tint_share
    out[plum, :3] = np.clip(col[plum] * shade[plum], 0.0, 1.0)
    print("texture", name, fname, "plumage texels", int(plum.sum()))
    return out


def _near_breed(px, name):
    """Texels already carrying the breed colour (a recoloured atlas), any shading."""
    tgt = np.array(PLUMAGE[name], dtype=np.float32) / 255.0
    lum = _luma(px[..., :3])
    ylum = float(_luma(tgt.reshape(1, 1, 3))[0, 0]) or 0.05
    scale = np.clip(lum / ylum, 0.50, 1.45)[..., None]
    return np.abs(px[..., :3] - tgt.reshape(1, 1, 3) * scale).sum(axis=2) < 0.12


def paint_breed(px_yellow, name, variant="chocobo"):
    """The shipped atlas for one breed from the Meshy (yellow) albedo."""
    if name == "yellow":
        return px_yellow
    out = recolor_plumage(px_yellow, name and PLUMAGE[name], variant)
    if name in TEXTURED:
        return texture_plumage(out, name, variant)
    return accent_plumage(out, name)
