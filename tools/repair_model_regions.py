"""Repair tail-root weights and anatomically misplaced beak/feather UV samples.

python tools/repair_model_regions.py
Uses a separate pre-region-repair backup so reruns are deterministic. Run after
polish_beaks.py; no remesh, rest-position change or animation change is required.
"""
from pathlib import Path
import struct
import numpy as np
from PIL import Image
from scipy import ndimage
from model_polish_audit import ROOT, read_mesh

ENTITY = ROOT / 'src/main/resources/assets/chocobosreborn/entity'
TEXTURES = ROOT / 'src/main/resources/assets/chocobosreborn/textures/entity'
BACKUP = ROOT / 'art/polish/pre-region-repair'
BACKUP.mkdir(parents=True, exist_ok=True)


def smoothstep(value):
    t = np.clip(value, 0, 1)
    return t * t * (3 - 2 * t)


def repair(path):
    original = BACKUP / path.name
    if not original.exists():
        original.write_bytes(path.read_bytes())
    names, parts, _ = read_mesh(original)
    assert len(parts) == 1
    _, vertices, triangles = parts[0]
    out = vertices.copy()
    p = vertices['pos']
    weights = np.zeros((len(vertices), len(names)), np.float32)
    for k in range(4):
        weights[np.arange(len(vertices)), vertices['bone'][:, k]] += vertices['weight'][:, k]
    body, tail = names.index('body'), names.index('tail')
    neck = [names.index(n) for n in ('neck', 'neck_mid', 'head', 'crest', 'crest_male')]
    root = float(np.max(p[weights[:, tail] > .05, 2]))
    rear = p[:, 2] < -.25
    mass = weights[:, [body, tail] + neck].sum(axis=1)
    blend = smoothstep((-p[:, 2] + root + .12) / .24) * smoothstep((p[:, 1] - .82) / .25)
    for b in [body, tail] + neck:
        weights[rear, b] = 0
    weights[rear, tail] = mass[rear] * blend[rear]
    weights[rear, body] = mass[rear] * (1 - blend[rear])
    # The wing's old height gate also jumped straight from body to wing.
    wing_fade = smoothstep((p[:, 1] - .90) / .10) * smoothstep((1.485 - p[:, 1]) / .10)
    wing_changed = np.zeros(len(vertices), bool)
    for name in ('wing_l', 'wing_r'):
        b = names.index(name)
        removed = weights[:, b] * (1 - wing_fade)
        wing_changed |= removed > 0
        weights[:, b] -= removed
        weights[:, body] += removed
    order = np.argsort(-weights, axis=1, kind='stable')[:, :4]
    packed = np.take_along_axis(weights, order, axis=1)
    packed /= packed.sum(axis=1)[:, None]
    changed = rear | wing_changed
    out['bone'][changed] = order[changed]
    out['weight'][changed] = packed[changed]

    atlases = [np.asarray(Image.open(TEXTURES / path.stem / f'{breed}.png').convert('RGB')) for breed in ('yellow', 'purple', 'flame')]
    atlas = atlases[0].astype(int)
    h, w = atlas.shape[:2]
    r, g, b = atlas.transpose(2, 0, 1)
    orange = (r > 160) & (g > 60) & (r - g >= 80) & (b < 95)
    for other in atlases[1:]:
        orange &= np.max(np.abs(other.astype(int) - atlas), axis=2) < 5
    feather = (r > 160) & (g > 120) & (b < 120) & (r - g < 70)
    # Interior samples have room around them, so texture filtering cannot hit an eye.
    safe_orange = ndimage.binary_erosion(orange, iterations=2)
    safe_feather = ndimage.binary_erosion(feather, iterations=2)
    assert safe_orange.any() and safe_feather.any()
    _, orange_donor = ndimage.distance_transform_edt(~safe_orange, return_indices=True)
    _, feather_donor = ndimage.distance_transform_edt(~safe_feather, return_indices=True)
    uv = vertices['uv'][triangles]
    samples = np.concatenate([uv, uv.mean(axis=1)[:, None, :],
                              (uv[:, 0] * .6 + uv[:, 1] * .2 + uv[:, 2] * .2)[:, None, :],
                              (uv[:, 0] * .2 + uv[:, 1] * .6 + uv[:, 2] * .2)[:, None, :],
                              (uv[:, 0] * .2 + uv[:, 1] * .2 + uv[:, 2] * .6)[:, None, :]], axis=1)
    sx = np.clip((samples[:, :, 0] * w).astype(int), 0, w - 1)
    sy = np.clip((samples[:, :, 1] * h).astype(int), 0, h - 1)
    colors = atlas[sy, sx]
    cr, cg, cb = colors.transpose(2, 0, 1)
    plum = (cr > 160) & (cg > 120) & (cb < 120) & (cr - cg < 70)
    beak_color = (cr > 140) & (cg > 50) & (cr - cg >= 70) & (cb < 100)
    centers = p[triangles].mean(axis=1)
    beak_faces = (centers[:, 1] > 1.60) & (centers[:, 1] < 1.925) & (centers[:, 2] > .70) & (np.abs(centers[:, 0]) < .205)
    core_beak = (centers[:, 2] > .79) & (centers[:, 1] < 1.895) & (np.abs(centers[:, 0]) < .16)
    bridge = (np.abs(centers[:, 0]) < .115) & (centers[:, 2] > .71) & (centers[:, 1] > 1.86) & (centers[:, 1] < 1.926)
    beak_faces &= (beak_color.mean(axis=1) >= 2 / 7) | (centers[:, 2] > .84) | core_beak | ((centers[:, 1] < 1.80) & (centers[:, 2] > .74))
    beak_faces |= bridge
    # A tuft is feather throughout. Wing faces need a feather majority to protect tack.
    tuft = centers[:, 1] > 2.045
    wing = (np.abs(centers[:, 0]) > .32) & (centers[:, 1] > .78) & (centers[:, 1] < 1.46) & (centers[:, 2] > -.55) & (centers[:, 2] < .35)
    feather_faces = (tuft & ((plum.mean(axis=1) >= .3) | (path.stem == 'chocobo'))) | (wing & (plum.mean(axis=1) >= .5))
    # Even a triangle with clean corners can cross a tiny eye/feather atlas island
    # internally. Give the identified beak surface a stable orange sample.
    repair_beak = beak_faces
    repair_feather = feather_faces & ~plum.all(axis=1)
    if path.stem != 'chocobo':
        neutral = (np.max(colors, axis=2) - np.min(colors, axis=2) < 35) & (np.min(colors, axis=2) > 90)
        leather = (cr < 170) & (cr > cg * 1.3) & (cb < cg * .85)
        protected_material = (neutral.mean(axis=1) >= .25) | (leather.mean(axis=1) >= .15)
        repair_beak &= ~protected_material
        repair_feather &= ~protected_material
    # Split the rare shared UV corners instead of skipping bridge faces or pulling
    # an adjacent eye/strap onto the beak sample. Positions/skin weights stay equal.
    uses = np.bincount(triangles.ravel(), minlength=len(vertices))
    new_triangles = triangles.copy()
    extras = []
    for selected, donors, valid in ((repair_beak, orange_donor, beak_color), (repair_feather, feather_donor, plum)):
        for face in np.flatnonzero(selected):
            # Stay near a good sample on this face when available; never interpolate
            # a replacement UV triangle across unrelated atlas islands.
            good = np.flatnonzero(valid[face])
            sample = int(good[0]) if len(good) else 3
            y, x = donors[:, sy[face, sample], sx[face, sample]]
            donor_uv = ((x + .5) / w, (y + .5) / h)
            for corner, vertex in enumerate(triangles[face]):
                if uses[vertex] > 1:
                    duplicate = out[vertex].copy()
                    duplicate['uv'] = donor_uv
                    new_triangles[face, corner] = len(vertices) + len(extras)
                    extras.append(duplicate)
                else:
                    out['uv'][vertex] = donor_uv
    if extras:
        out = np.concatenate([out, np.asarray(extras, dtype=out.dtype)])
    data = bytearray(original.read_bytes())
    offset = 12
    for _ in names:
        length, = struct.unpack_from('<H', data, offset)
        offset += 2 + length + 4
    offset += 4
    length, = struct.unpack_from('<H', data, offset)
    offset += 2 + length + 5
    suffix = offset + vertices.nbytes + 4 + triangles.nbytes
    data = (data[:offset - 4] + struct.pack('<I', len(out)) + out.tobytes()
            + struct.pack('<I', len(new_triangles)) + new_triangles.tobytes() + data[suffix:])
    temporary = path.with_suffix('.ncgb.tmp')
    temporary.write_bytes(data)
    temporary.replace(path)
    print(path.stem, 'tail weight vertices', int(np.any(out['weight'][:len(vertices)] != vertices['weight'], axis=1).sum()),
          'beak faces', int(repair_beak.sum()), 'tuft/wing faces', int(repair_feather.sum()))


if __name__ == '__main__':
    for path in sorted(ENTITY.glob('*.ncgb')):
        repair(path)
