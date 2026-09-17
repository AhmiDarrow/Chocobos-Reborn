"""Fail-closed visual gate. Every adult / female / baby / breed must pass.

    python tools/preview_qa.py
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PREVIEW = ROOT / "art/preview"

ORANGE = np.array([230, 102, 15], dtype=np.float32) / 255.0
BROWN = np.array([102, 56, 31], dtype=np.float32) / 255.0  # still-8 milk chocolate
YELLOW = np.array([245, 184, 18], dtype=np.float32) / 255.0
BREEDS = {
    "yellow": (245, 184, 18),
    "green": (76, 176, 90),
    "blue": (58, 143, 208),
    "white": (232, 228, 220),
    "black": (44, 42, 50),
    "gold": (255, 210, 74),
    "purple": (146, 84, 214),
    "flame": (190, 42, 30),
}
REQUIRED = (
    ["meshy_char_male.png", "meshy_char_male_front.png", "meshy_char_female.png"]
    + [f"meshy_char_{b}.png" for b in BREEDS]
    + ["meshy_char_baby0.png", "meshy_char_baby1.png", "meshy_char_baby2.png"]
)


def load(name: str) -> np.ndarray:
    p = PREVIEW / name
    if not p.is_file():
        raise FileNotFoundError(p)
    return np.asarray(Image.open(p).convert("RGB"), dtype=np.float32) / 255.0


def bird_mask(rgb: np.ndarray) -> np.ndarray:
    mx = rgb.max(axis=2)
    sat = mx - rgb.min(axis=2)
    return (mx < 0.88) | (sat > 0.07)


def bbox(mask: np.ndarray):
    ys, xs = np.nonzero(mask)
    if ys.size < 80:
        return None
    return int(ys.min()), int(ys.max()), int(xs.min()), int(xs.max())


def crop(rgb, mask, box):
    y0, y1, x0, x1 = box
    return rgb[y0 : y1 + 1, x0 : x1 + 1], mask[y0 : y1 + 1, x0 : x1 + 1]


def dist(rgb, color) -> np.ndarray:
    return np.sqrt(((rgb - color) ** 2).sum(axis=2))


def _blobs(mask: np.ndarray) -> int:
    from scipy.ndimage import label

    _, n = label(mask)
    return int(n)


def qa_common(rgb, tag, errors, *, white_bird=False, skip_yellow=False):
    mask = bird_mask(rgb)
    box = bbox(mask)
    if box is None:
        errors.append(f"{tag}: no bird in frame")
        return None
    y0, y1, x0, x1 = box
    h, w = y1 - y0 + 1, x1 - x0 + 1
    sub, sm = crop(rgb, mask, box)
    n = int(sm.sum()) or 1
    orange = sm & (dist(sub, ORANGE) < 0.38) & (sub[:, :, 0] > sub[:, :, 1] + 0.08)
    brown = sm & (dist(sub, BROWN) < 0.32) & (sub.max(axis=2) < 0.50)
    yellow = sm & (dist(sub, YELLOW) < 0.28)
    head = np.zeros_like(sm)
    head[: int(0.42 * h)] = sm[: int(0.42 * h)]
    lum = sub.mean(axis=2)
    sat = sub.max(axis=2) - sub.min(axis=2)
    sclera = head & (lum > 0.72) & (sat < 0.22)
    blue = head & (sub[:, :, 2] > 0.18) & (sub[:, :, 2] > sub[:, :, 0] + 0.05)
    dark = head & (lum < 0.28)
    n_sclera, n_blue, n_dark = int(sclera.sum()), int(blue.sum()), int(dark.sum())
    n_orange, n_brown, n_yellow = int(orange.sum()), int(brown.sum()), int(yellow.sum())
    print(
        f"{tag}: {w}x{h} n={n} Y={n_yellow} O={n_orange} Br={n_brown} "
        f"sclera={n_sclera} blue={n_blue} dark={n_dark}"
    )
    if n_orange < 30:
        errors.append(f"{tag}: beak missing (orange {n_orange})")
    if n_brown < 80:
        errors.append(f"{tag}: legs missing (brown {n_brown})")
    if not skip_yellow and n_yellow < 300:
        errors.append(f"{tag}: plumage too thin (yellow {n_yellow})")
    if white_bird:
        if n_blue < 8:
            errors.append(f"{tag}: white bird has no iris ({n_blue} blue px)")
    else:
        if n_sclera < 25:
            errors.append(f"{tag}: no readable eyes (sclera {n_sclera})")
        if n_blue + n_dark < 6:
            errors.append(f"{tag}: eyes have no pupil/iris")
        if n_sclera >= 25 and _blobs(sclera) < 2:
            errors.append(f"{tag}: eye visor ({_blobs(sclera)} sclera blob)")
    return sub, sm, n, h, w


def qa_female(rgb, errors):
    got = qa_common(rgb, "female", errors)
    if got is None:
        return
    _, sm, n, h, w = got
    head = sm[: int(0.42 * h)]
    if float(head.sum()) / n < 0.12:
        errors.append("female: head collapsed")
    if h < 0.55 * w:
        errors.append(f"female: squat silhouette {w}x{h}")


def qa_baby(rgb, tag, errors):
    mask = bird_mask(rgb)
    box = bbox(mask)
    if box is None:
        errors.append(f"{tag}: no bird")
        return
    y0, y1, x0, x1 = box
    h, w = y1 - y0 + 1, x1 - x0 + 1
    sub, sm = crop(rgb, mask, box)
    n = int(sm.sum()) or 1
    # Full bird must be in frame: not a torso crop (head glued to top edge).
    top_margin = y0
    if top_margin < 8:
        errors.append(f"{tag}: head clipped at top of frame")
    head = sm[: int(0.36 * h), : int(0.48 * w)]
    body = sm[int(0.40 * h) :]
    head_a = float(head.sum()) / n
    head_w = int(np.any(head, axis=0).sum()) or 1
    body_w = int(np.any(body, axis=0).sum()) or 1
    print(f"{tag}: {w}x{h} head_frac={head_a:.2f} head_w={head_w} body_w={body_w} y0={y0}")
    if head_a > 0.32:
        errors.append(f"{tag}: fat head ({head_a:.2f})")
    if head_w > 0.85 * body_w:
        errors.append(f"{tag}: head wider than body ({head_w} vs {body_w})")
    orange = sm & (dist(sub, ORANGE) < 0.38) & (sub[:, :, 0] > sub[:, :, 1] + 0.08)
    if int(orange.sum()) < 8:
        errors.append(f"{tag}: beak missing")
    lum = sub.mean(axis=2)
    sat = sub.max(axis=2) - sub.min(axis=2)
    hd = np.zeros_like(sm)
    hd[: int(0.50 * h)] = sm[: int(0.50 * h)]
    sclera = hd & (lum > 0.72) & (sat < 0.22)
    if int(sclera.sum()) < 8:
        errors.append(f"{tag}: no eyes")


def qa_breed(rgb, name, target, errors):
    white = name == "white"
    skip_y = name not in ("yellow", "gold")
    got = qa_common(rgb, name, errors, white_bird=white, skip_yellow=skip_y)
    if got is None:
        return
    sub, sm, n, h, w = got
    tgt = np.array(target, dtype=np.float32) / 255.0
    orange = (dist(sub, ORANGE) < 0.38) & (sub[:, :, 0] > sub[:, :, 1] + 0.08)
    brown = (dist(sub, BROWN) < 0.32) & (sub.max(axis=2) < 0.50)
    lum = sub.mean(axis=2)
    sat = sub.max(axis=2) - sub.min(axis=2)
    sclera = (lum > 0.72) & (sat < 0.22)
    plum = sm & ~orange & ~brown & ~sclera
    if int(plum.sum()) < 200:
        errors.append(f"{name}: no plumage mask")
        return
    mean = sub[plum].mean(axis=0)
    d_tgt = float(np.linalg.norm(mean - tgt))
    d_yel = float(np.linalg.norm(mean - YELLOW))
    print(f"{name}: plumage mean {tuple(round(float(c), 3) for c in mean)} d_tgt={d_tgt:.3f} d_yel={d_yel:.3f}")
    if name == "white":
        # White plumage is near studio gray; the mask under-counts. Require
        # the visible bird not be gold-dominant.
        if d_yel + 0.05 < d_tgt and n > 80000:
            errors.append(f"{name}: plumage still yellow")
        return
    if name not in ("yellow", "gold", "flame") and d_tgt > d_yel + 0.04:
        errors.append(f"{name}: plumage still yellow (d_tgt {d_tgt:.3f} > d_yel {d_yel:.3f})")
    if d_tgt > 0.58:
        errors.append(f"{name}: plumage far from breed ({d_tgt:.3f})")


def main() -> int:
    errors: list[str] = []
    for name in REQUIRED:
        if not (PREVIEW / name).is_file():
            errors.append(f"missing {name}")
    if errors:
        print("QA FAIL")
        for e in errors:
            print(" -", e)
        return 1
    qa_common(load("meshy_char_male.png"), "male", errors)
    qa_common(load("meshy_char_male_front.png"), "male_front", errors)
    qa_female(load("meshy_char_female.png"), errors)
    for b, rgb in BREEDS.items():
        qa_breed(load(f"meshy_char_{b}.png"), b, rgb, errors)
    for i in range(3):
        qa_baby(load(f"meshy_char_baby{i}.png"), f"baby{i}", errors)
    if errors:
        print("QA FAIL")
        for e in errors:
            print(" -", e)
        print("Do not paste these previews into chat. Fix and re-render.")
        return 1
    print("QA PASS all", len(REQUIRED), "stills")
    return 0


if __name__ == "__main__":
    sys.exit(main())
