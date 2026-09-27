"""Clean the existing eye paint in place, preserving its layout and highlights.

Only UV texels belonging to the eye neighbourhood are eligible. Blue irises get
a coherent two-tone palette, pupils a cool near-black, whites a clean ivory.
No geometry, UV coordinates or eye placement changes. Originals are backed up.
"""
import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage
from model_polish_audit import ROOT, read_mesh

BACKUP = ROOT / 'art/polish/eye-originals'
TEX = ROOT / 'src/main/resources/assets/chocobosreborn/textures/entity'

# Authored eye islands in the shipped 1024 atlases, inspected against the mesh.
# Their small detached fragments are included; nearby tack/helmet islands are not.
EYE_BOXES = {
    'chocobo': [(459, 406, 511, 446), (348, 960, 382, 1001), (353, 766, 379, 793), (652, 365, 676, 405)],
    'chocobo_saddled': [(650, 421, 698, 472), (326, 650, 369, 706), (312, 910, 341, 938)],
    'chocobo_armor_iron': [(132, 581, 166, 626), (127, 772, 165, 813), (399, 106, 436, 144)],
    'chocobo_armor_diamond': [(510, 92, 557, 137), (45, 88, 81, 124), (722, 300, 758, 344)],
}

def eye_islands(variant, shape):
    mask = np.zeros(shape, bool)
    for x0, y0, x1, y1 in EYE_BOXES[variant]:
        mask[y0:y1, x0:x1] = True
    return mask


def eye_faces(vertices, triangles, atlas):
    centers = vertices['pos'][triangles].mean(axis=1)
    uv = vertices['uv'][triangles].mean(axis=1)
    h, w = atlas.shape[:2]
    rgb = atlas[np.clip((uv[:, 1] * h).astype(int), 0, h - 1), np.clip((uv[:, 0] * w).astype(int), 0, w - 1)].astype(int)
    r, g, b = rgb.T
    iris = (b > g + 35) & (b > r + 40) & (r < 110)
    iris &= (centers[:, 1] > 1.77) & (centers[:, 1] < 1.99) & (centers[:, 2] > .60) & (centers[:, 2] < .80)
    selected = np.zeros(len(triangles), bool)
    for side in (-1, 1):
        candidates = iris & (centers[:, 0] * side > 0)
        if not candidates.any():
            continue
        center = np.median(centers[candidates], axis=0)
        d = np.abs(centers - center)
        # Eyes are painted on the front plane. Nearby silver buckles and blue
        # helmet surfaces must not be mistaken for the same palette.
        selected |= ((d[:, 0] < .13) & (centers[:, 1] > 1.74)
                     & (centers[:, 1] < 1.96) & (d[:, 2] < .10)
                     & (centers[:, 0] * side > .055))
    return selected


def main():
    for model in sorted((ROOT / 'src/main/resources/assets/chocobosreborn/entity').glob('*.ncgb')):
        folder = BACKUP / model.stem
        folder.mkdir(parents=True, exist_ok=True)
        for breed in ('yellow', 'purple', 'flame'):
            source = TEX / model.stem / f'{breed}.png'
            backup = folder / source.name
            if not backup.exists():
                backup.write_bytes(source.read_bytes())
        yellow = np.asarray(Image.open(folder / 'yellow.png').convert('RGB'))
        h, w = yellow.shape[:2]
        # Do not paint across the border into a neighbouring atlas island.
        mask = eye_islands(model.stem, (h, w))
        brow = np.zeros((h, w), bool)
        if model.stem in ('chocobo', 'chocobo_saddled'):
            _, parts, _ = read_mesh(model)
            _, vertices, triangles = parts[0]
            centers = vertices['pos'][triangles].mean(axis=1)
            selected = ((centers[:, 1] > 1.935) & (centers[:, 1] < 2.04)
                        & (centers[:, 2] > .55) & (centers[:, 2] < .78))
            region = Image.new('L', (w, h))
            draw = ImageDraw.Draw(region)
            for face in triangles[selected]:
                draw.polygon([tuple(p) for p in vertices['uv'][face] * (w, h)], fill=255)
            rgb = yellow.astype(int)
            brow = (np.asarray(region) > 0) & (rgb.min(axis=2) > 180) & (rgb.max(axis=2) - rgb.min(axis=2) < 35)
        yr, yg, yb = yellow.astype(int).transpose(2, 0, 1)
        feather = (yr > 160) & (yg > 120) & (yr - yg < 70) & (yb < 100)
        nearest_feather = ndimage.distance_transform_edt(~feather, return_distances=False, return_indices=True)
        for breed in ('yellow', 'purple', 'flame'):
            original = np.asarray(Image.open(folder / f'{breed}.png').convert('RGB'))
            rgb = original.astype(int)
            r, g, b = rgb.transpose(2, 0, 1)
            white = (rgb.min(axis=2) > 190) & (rgb.max(axis=2) - rgb.min(axis=2) < 35)
            pupil = rgb.max(axis=2) < 65
            blue = (b > g + 25) & (b > r + 30) & (r < 120)
            labels = np.zeros((h, w), np.uint8)
            labels[white] = 1
            labels[pupil] = 2
            labels[blue] = 3
            # Remove isolated label specks only when five neighbours agree; preserve
            # the existing blue/white boundary and the original catchlight shape.
            clean = labels.copy()
            for label in (1, 2, 3):
                count = ndimage.convolve((labels == label).astype(np.uint8), np.ones((3, 3), np.uint8), mode='constant')
                clean[(labels != label) & (count >= 6) & mask] = label
            out = original.copy()
            out[mask & (clean == 1)] = (245, 248, 250)
            out[mask & (clean == 2)] = (15, 22, 32)
            out[mask & (clean == 3) & (b < 150)] = (27, 65, 121)
            out[mask & (clean == 3) & (b >= 150)] = (46, 108, 190)
            out[brow] = original[tuple(nearest_feather[:, brow])]
            assert np.array_equal(out[~(mask | brow)], original[~(mask | brow)])
            target = TEX / model.stem / f'{breed}.png'
            temporary = target.with_suffix('.tmp.png')
            Image.fromarray(out).save(temporary, optimize=True)
            temporary.replace(target)
            print(model.stem, breed, 'eye texels cleaned', int(np.any(out != original, axis=2).sum()))


if __name__ == "__main__":
    main()
