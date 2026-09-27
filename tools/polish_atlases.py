"""Repair previously untinted yellow flecks without repainting authored accents.

Run after a plumage guard change. Repairs only texels still matching the yellow
source in existing purple/flame atlases; eyes, tack and dimension patterns stay.
Works with shipped atlases and, when present, local 2048 masters. Idempotent.
"""
from pathlib import Path
import numpy as np
from PIL import Image
from scipy import ndimage
from paint_albedo import PLUMAGE, plumage_mask, recolor_plumage

ROOT = Path(__file__).resolve().parents[1]
VARIANTS = ('chocobo', 'chocobo_saddled', 'chocobo_armor_iron', 'chocobo_armor_diamond')


def polish(folder, variant):
    yellow = np.asarray(Image.open(folder / 'yellow.png').convert('RGB'))
    px = yellow.astype(np.float32) / 255
    plum = plumage_mask(px)
    mask_path = ROOT / 'art/masks' / f'{variant}_feathers.png'
    feathers = np.zeros(plum.shape, bool)
    if mask_path.exists():
        feathers = np.asarray(Image.open(mask_path).convert('L').resize((yellow.shape[1], yellow.shape[0]), Image.Resampling.NEAREST)) > 127
    for breed in ('purple', 'flame'):
        path = folder / f'{breed}.png'
        original = Image.open(path)
        target = np.asarray(original.convert('RGB')).copy()
        delta = np.max(np.abs(target.astype(int) - yellow.astype(int)), axis=2)
        repair = plum & (delta < 4)
        if not repair.any():
            print(path, 'already clean')
            continue
        recolored = np.clip(recolor_plumage(px, PLUMAGE[breed]) * 255 + 0.5, 0, 255).astype(np.uint8)
        replacement = recolored.copy()
        # Continue the neighbouring authored feather treatment across a missed texel.
        for region in (feathers, ~feathers):
            donors = plum & region & (delta > 30)
            if not donors.any():
                continue
            distance, (iy, ix) = ndimage.distance_transform_edt(~donors, return_indices=True)
            use = repair & region & (distance <= 8)
            source_luma = np.maximum(1, yellow.astype(float) @ [0.2126, 0.7152, 0.0722])
            ratio = np.clip(source_luma / source_luma[iy, ix], 0.8, 1.2)
            replacement[use] = np.clip(target[iy[use], ix[use]] * ratio[use, None] + 0.5, 0, 255).astype(np.uint8)
        target[repair] = replacement[repair]
        output = Image.fromarray(target)
        if original.mode == 'RGBA':
            output = output.convert('RGBA')
            output.putalpha(original.getchannel('A'))
        output.save(path, optimize=True)
        print(path, 'repaired', int(repair.sum()), 'texels')


if __name__ == '__main__':
    for root in (ROOT / 'src/main/resources/assets/chocobosreborn/textures/entity', ROOT / 'art/atlases'):
        for variant in VARIANTS:
            folder = root / variant
            if (folder / 'yellow.png').exists():
                polish(folder, variant)
