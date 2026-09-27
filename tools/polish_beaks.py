"""Blender geometry polish of shipped beaks; retains UVs, weights and clip bytes.

blender -b -P tools/polish_beaks.py
Original NCGBs are saved in art/polish/originals before editing. Repeated runs use
those originals, never accumulate smoothing. Only positions/normals are patched.
"""
import sys
import struct
from pathlib import Path
import bpy
import bmesh
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from model_polish_audit import read_mesh, VERTEX

FOLDER = ROOT / 'src/main/resources/assets/chocobosreborn/entity'
BACKUP = ROOT / 'art/polish/originals'
BACKUP.mkdir(parents=True, exist_ok=True)


def polish(path):
    original = BACKUP / path.name
    if not original.exists():
        original.write_bytes(path.read_bytes())
    bones, parts, clips = read_mesh(original)
    assert len(parts) == 1
    name, v, triangles = parts[0]
    points, inverse = np.unique(v['pos'], axis=0, return_inverse=True)
    faces = inverse[triangles]
    atlas = np.asarray(Image.open(ROOT / 'src/main/resources/assets/chocobosreborn/textures/entity' / path.stem / 'yellow.png').convert('RGB')).astype(int)
    h, w = atlas.shape[:2]
    uv = v['uv']
    col = atlas[np.clip((uv[:, 1] * h).astype(int), 0, h - 1), np.clip((uv[:, 0] * w).astype(int), 0, w - 1)]
    r, g, b = col.T
    # Only the orange beak at the front of the head; excludes saddle, crest and eyes.
    orange = (r - g >= 70) & (r > 140) & (g > 50) & (b < 100)
    orange &= (v['pos'][:, 1] > 1.5) & (v['pos'][:, 1] < 1.98) & (v['pos'][:, 2] > 0.65)
    seed = v['pos'][orange]
    lo, hi = seed.min(axis=0), seed.max(axis=0)
    selection = np.all((points >= lo - [0.015, 0.015, 0]) & (points <= hi + [0.015, 0.015, 0.005]), axis=1)
    # Pin the attachment ring. The relief is on the beak itself, not its head seam.
    selection &= points[:, 2] > float(np.quantile(seed[:, 2], 0.25))
    # A shared corner can also belong to an eye or bridle face. Pin those
    # corners rather than letting a bounding-box selection drag nearby features.
    protected = (np.min(col, axis=1) > 180) | (b > r + 20) | (r + g + b < 220)
    pinned = np.zeros(len(points), bool)
    np.logical_or.at(pinned, inverse, protected)
    selection &= ~pinned
    bm = bmesh.new()
    for point in points:
        bm.verts.new(point)
    bm.verts.ensure_lookup_table()
    for face in faces:
        bm.faces.new([bm.verts[int(i)] for i in face])
    selected = [bm.verts[i] for i in np.flatnonzero(selection)]
    for _ in range(6):
        bmesh.ops.smooth_vert(bm, verts=selected, factor=0.4, use_axis_x=True, use_axis_y=True, use_axis_z=True)
    result = np.array([tuple(vertex.co) for vertex in bm.verts], dtype=np.float32)
    bm.free()
    delta = result - points
    # Fill concave relief only. Shrinking convex corners exposes overlapping
    # source panels and can reveal cyan armour texels inside the beak seam.
    vertex_normals = np.zeros_like(points)
    np.add.at(vertex_normals, inverse, v['normal'])
    vertex_normals /= np.maximum(np.linalg.norm(vertex_normals, axis=1)[:, None], 1e-12)
    relief = np.maximum(0, np.sum(delta * vertex_normals, axis=1))
    delta = vertex_normals * relief[:, None]
    # Keep overlapping source panels seated: fill at most 6mm of local relief.
    length = np.linalg.norm(delta, axis=1)
    delta *= np.minimum(1, 0.006 / np.maximum(length, 1e-10))[:, None]
    result = points + delta
    old_cross = np.cross(points[faces[:, 1]] - points[faces[:, 0]], points[faces[:, 2]] - points[faces[:, 0]])
    # Back off locally wherever a skinny triangle would turn inside out.
    for _ in range(20):
        new_cross = np.cross(result[faces[:, 1]] - result[faces[:, 0]], result[faces[:, 2]] - result[faces[:, 0]])
        bad = np.sum(old_cross * new_cross, axis=1) <= 0
        if not bad.any():
            break
        ids = np.unique(faces[bad])
        delta[ids] *= 0.5
        result[ids] = points[ids] + delta[ids]
    assert not bad.any(), 'polish would invert a face'
    moved = np.linalg.norm(result - points, axis=1) > 1e-7
    assert moved.any() and not moved[~selection].any()
    edited = v.copy()
    edited['pos'] = result[inverse]
    changed_faces = np.any(moved[faces], axis=1)
    normals = new_cross / np.maximum(np.linalg.norm(new_cross, axis=1)[:, None], 1e-12)
    # Keep original winding/normal orientation and flat shading.
    flip = np.sum(normals * v['normal'][triangles[:, 0]], axis=1) < 0
    normals[flip] *= -1
    for j in range(3):
        edited['normal'][triangles[changed_faces, j]] = normals[changed_faces]
    data = bytearray(original.read_bytes())
    # NCGB header: bones, part count, one part's name and vertex count.
    offset = 12
    for _ in bones:
        n, = struct.unpack_from('<H', data, offset)
        offset += 2 + n + 4
    offset += 4
    n, = struct.unpack_from('<H', data, offset)
    offset += 2 + n + 1 + 4
    data[offset:offset + edited.nbytes] = edited.tobytes()
    assert len(data) == original.stat().st_size
    path.write_bytes(data)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    mesh = bpy.data.meshes.new(name)
    blender_positions = result[:, [0, 2, 1]].copy()
    blender_positions[:, 1] *= -1
    mesh.from_pydata(blender_positions.tolist(), [], faces.tolist())
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    uv_layer = mesh.uv_layers.new(name='UVMap')
    for poly, triangle in zip(mesh.polygons, triangles):
        for loop, vertex in zip(poly.loop_indices, triangle):
            uv_layer.data[loop].uv = (v['uv'][vertex, 0], 1 - v['uv'][vertex, 1])
    material = bpy.data.materials.new('yellow')
    material.use_nodes = True
    image_node = material.node_tree.nodes.new('ShaderNodeTexImage')
    image_node.image = bpy.data.images.load(str(ROOT / 'src/main/resources/assets/chocobosreborn/textures/entity' / path.stem / 'yellow.png'))
    image_node.image.pack()
    image_node.interpolation = 'Closest'
    material.node_tree.links.new(image_node.outputs['Color'], material.node_tree.nodes['Principled BSDF'].inputs['Base Color'])
    mesh.materials.append(material)
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    bpy.ops.wm.save_as_mainfile(filepath=str(ROOT / 'art/polish' / f'{path.stem}_beak.blend'))
    print(path.stem, 'beak vertices polished', int(moved.sum()), 'faces', int(changed_faces.sum()), 'max displacement', float(np.linalg.norm(result - points, axis=1).max()))


for path in sorted(FOLDER.glob('*.ncgb')):
    polish(path)
