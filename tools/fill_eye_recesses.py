"""Lift recessed eye-panel vertices without deleting faces or opening seams.

Run after repair_model_regions.py. Uses its own backup for deterministic reruns.
Coincident UV corners move together. Existing faces and animation stay intact;
the plain model also receives a closed 12-triangle brow/beak bridge insert.
"""
import struct
import numpy as np
from PIL import Image
from model_polish_audit import ROOT, read_mesh, boundary_count

BACKUP = ROOT / 'art/polish/pre-eye-relief'
BACKUP.mkdir(parents=True, exist_ok=True)
PANELS = {
    'chocobo': (.732, .125, .245),
    'chocobo_saddled': (.740, .125, .240),
    'chocobo_armor_iron': (.768, .115, .213),
    'chocobo_armor_diamond': (.669, .060, .195),
}

for name, (depth, inner, outer) in PANELS.items():
    path = ROOT / 'src/main/resources/assets/chocobosreborn/entity' / f'{name}.ncgb'
    backup = BACKUP / path.name
    if not backup.exists():
        backup.write_bytes(path.read_bytes())
    bones, parts, _ = read_mesh(backup)
    _, vertices, triangles = parts[0]
    points, inverse = np.unique(vertices['pos'], axis=0, return_inverse=True)
    faces = inverse[triangles]
    selected = ((abs(points[:, 0]) > inner) & (abs(points[:, 0]) < outer)
                & (points[:, 1] > 1.795) & (points[:, 1] < 1.925)
                & (points[:, 2] > depth - .026) & (points[:, 2] < depth - .001))
    result = points.copy()
    # Retain a tiny depth difference so thin inset side faces remain valid.
    result[selected, 2] += (depth - .0005 - result[selected, 2]) * .97
    old_cross = np.cross(points[faces[:, 1]] - points[faces[:, 0]], points[faces[:, 2]] - points[faces[:, 0]])
    for _ in range(24):
        cross = np.cross(result[faces[:, 1]] - result[faces[:, 0]], result[faces[:, 2]] - result[faces[:, 0]])
        bad = (np.sum(old_cross * cross, axis=1) <= 0) & (np.linalg.norm(old_cross, axis=1) > 1e-12)
        if not bad.any():
            break
        ids = np.unique(faces[bad])
        result[ids] = (result[ids] + points[ids]) * .5
    assert not bad.any(), 'eye relief must not invert triangles'
    out = vertices.copy()
    out['pos'] = result[inverse]
    changed = np.any(np.linalg.norm(result[faces] - points[faces], axis=2) > 1e-7, axis=1)
    normals = cross / np.maximum(np.linalg.norm(cross, axis=1)[:, None], 1e-12)
    normals[np.sum(normals * vertices['normal'][triangles[:, 0]], axis=1) < 0] *= -1
    for corner in range(3):
        out['normal'][triangles[changed, corner]] = normals[changed]
    assert boundary_count(out['pos'], triangles) <= boundary_count(vertices['pos'], triangles)
    final_triangles = triangles
    if name == 'chocobo':
        # Fit the inner brow-to-beak opening, following its asymmetric rim.
        # A closed volume seats behind that rim rather than a floating plane.
        front = np.array([[-.077, 1.894, .756], [.148, 1.881, .756],
                          [.171, 1.918, .756], [-.097, 1.922, .756]], dtype=np.float32)
        back = front.copy()
        back[:, 2] = .700
        corners = np.concatenate([back, front])
        atlas = np.asarray(Image.open(ROOT / 'src/main/resources/assets/chocobosreborn/textures/entity' / name / 'yellow.png').convert('RGB')).astype(int)
        uv = vertices['uv']
        colors = atlas[(uv[:, 1] * 1024).astype(int), (uv[:, 0] * 1024).astype(int)]
        safe = (colors[:, 0] > 160) & (colors[:, 1] > 60) & (colors[:, 0] - colors[:, 1] >= 80) & (colors[:, 2] < 95)
        candidates = np.flatnonzero(safe)
        donor = candidates[np.argmin(np.linalg.norm(vertices['pos'][candidates] - [0, 1.90, .756], axis=1))]
        extra = np.repeat(vertices[donor:donor+1], 24)
        extra['bone'] = 0
        extra['bone'][:, 0] = bones.index('head')
        extra['weight'] = 0
        extra['weight'][:, 0] = 1
        added = []
        for i, quad in enumerate([(4,5,6,7), (1,0,3,2), (0,4,7,3), (5,1,2,6), (3,7,6,2), (0,1,5,4)]):
            xyz = corners[list(quad)]
            normal = np.cross(xyz[1] - xyz[0], xyz[2] - xyz[0])
            extra['pos'][i*4:i*4+4] = xyz
            extra['normal'][i*4:i*4+4] = normal / np.linalg.norm(normal)
            start = len(out) + i*4
            added.extend([(start,start+1,start+2), (start,start+2,start+3)])
        assert boundary_count(extra['pos'], np.asarray(added) - len(out)) == 0
        out = np.concatenate([out, extra])
        final_triangles = np.concatenate([triangles, np.asarray(added, dtype='<u4')])
    data = bytearray(backup.read_bytes())
    offset = 12
    for _ in bones:
        size, = struct.unpack_from('<H', data, offset)
        offset += 2 + size + 4
    offset += 4
    size, = struct.unpack_from('<H', data, offset)
    offset += 2 + size + 1 + 4
    suffix = offset + vertices.nbytes + 4 + triangles.nbytes
    data = data[:offset-4] + struct.pack('<I', len(out)) + out.tobytes() + struct.pack('<I', len(final_triangles)) + final_triangles.tobytes() + data[suffix:]
    temporary = path.with_suffix('.ncgb.tmp')
    temporary.write_bytes(data)
    temporary.replace(path)
    print(name, 'eye vertices lifted', int(np.count_nonzero(np.linalg.norm(result - points, axis=1) > 1e-7)), 'no added boundary edges')
