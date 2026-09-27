"""Bake transparent eyelid overlays in the existing UV layout (one per outfit).

The runtime tints this small overlay and caches closed-eye textures. No animated
PNG strips, geometry changes or per-frame image processing are needed.
"""
import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage
from model_polish_audit import ROOT, read_mesh
from polish_eye_atlases import eye_faces, eye_islands

TEX = ROOT / 'src/main/resources/assets/chocobosreborn/textures/entity'
for model in sorted((ROOT / 'src/main/resources/assets/chocobosreborn/entity').glob('*.ncgb')):
    _, parts, _ = read_mesh(model)
    _, vertices, triangles = parts[0]
    atlas = np.asarray(Image.open(TEX / model.stem / 'yellow.png').convert('RGB'))
    h, w = atlas.shape[:2]
    selected = eye_faces(vertices, triangles, atlas)
    coverage = Image.new('L', (w, h))
    draw = ImageDraw.Draw(coverage)
    height_map = np.zeros((h, w), np.float32)
    centers = vertices['pos'][triangles].mean(axis=1)
    uv = vertices['uv']
    for face in triangles[selected]:
        points = uv[face] * (w, h)
        draw.polygon([tuple(point) for point in points], fill=255)
        x0, y0 = np.maximum(0, np.floor(points.min(axis=0)).astype(int))
        x1, y1 = np.minimum([w - 1, h - 1], np.ceil(points.max(axis=0)).astype(int))
        if x1 < x0 or y1 < y0:
            continue
        a, b, c = points
        determinant = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
        if abs(determinant) < 1e-7:
            continue
        yy, xx = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        qx, qy = xx + .5 - a[0], yy + .5 - a[1]
        wb = (qx * (c[1] - a[1]) - qy * (c[0] - a[0])) / determinant
        wc = ((b[0] - a[0]) * qy - (b[1] - a[1]) * qx) / determinant
        wa = 1 - wb - wc
        inside = (wa >= -.03) & (wb >= -.03) & (wc >= -.03)
        values = wa * vertices['pos'][face[0], 1] + wb * vertices['pos'][face[1], 1] + wc * vertices['pos'][face[2], 1]
        view = height_map[y0:y1 + 1, x0:x1 + 1]
        view[inside] = values[inside]
    rgb = atlas.astype(int)
    r, g, b = rgb.transpose(2, 0, 1)
    neutral = ((rgb.min(axis=2) > 200) & (rgb.max(axis=2) - rgb.min(axis=2) < 35)) | (rgb.max(axis=2) < 65)
    blue = (b > r + 25) & (b > g + 15)
    covered = eye_islands(model.stem, (h, w))
    mask = covered & (neutral | blue)
    # Preserve edge pixels: closing alone erodes the narrow UV-island borders.
    mask |= ndimage.binary_closing(mask, iterations=1) & covered
    fragment_coverage = ndimage.binary_dilation(np.asarray(coverage) > 0, iterations=2)
    missing_height = (height_map == 0) & (mask | fragment_coverage)
    nearest = ndimage.distance_transform_edt(height_map == 0, return_distances=False, return_indices=True)
    height_map[missing_height] = height_map[tuple(nearest[:, missing_height])]
    # The remesher scattered tiny edge fragments outside the main UV islands.
    # Recover only bright sclera / saturated iris on the eye-height geometry;
    # neutral armor and dark leather remain excluded from this recovery.
    top = 1.935 if model.stem in ('chocobo', 'chocobo_saddled') else 1.915
    fragments = fragment_coverage & (height_map > 1.765) & (height_map < top)
    fragments &= (((rgb.min(axis=2) > 190) & (rgb.max(axis=2) - rgb.min(axis=2) < 35)) | ((b > g * 1.6) & (b > r * 1.8)))
    mask |= fragments
    # A small atlas gutter covers antialiased sclera edges during the blink.
    # Keep orange and dark tack out of that gutter.
    gutter = ndimage.binary_dilation(mask, iterations=2) & ~mask
    gutter &= (rgb.min(axis=2) > 100) & ((r - g) < 80)
    nearest = ndimage.distance_transform_edt(~mask, return_distances=False, return_indices=True)
    height_map[gutter] = height_map[tuple(nearest[:, gutter])]
    mask |= gutter
    median_height = float(np.median(height_map[mask & (height_map > 0)]))
    lid = np.zeros((h, w, 4), np.uint8)
    lid[mask] = (245, 213, 35, 255)
    line = mask & (np.abs(height_map - median_height) < .007)
    lid[line] = (46, 35, 22, 255)
    target = TEX / model.stem / 'eyes_blink.png'
    Image.fromarray(lid).save(target, optimize=True)
    print(model.stem, 'eyelid pixels', int(mask.sum()), 'lid seam', int(line.sum()))
