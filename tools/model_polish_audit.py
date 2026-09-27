"""Inspect the actual shipped NCGB surfaces, including the female crest cut."""
from pathlib import Path
import struct
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
VERTEX = np.dtype([('pos', '<f4', 3), ('normal', '<f4', 3), ('uv', '<f4', 2),
                   ('rgb', 'u1', 3), ('emit', 'u1', 3), ('bone', '<u2', 4), ('weight', '<f4', 4)])


def read_mesh(path):
    data = path.read_bytes()
    offset = 0

    def unpack(fmt):
        nonlocal offset
        result = struct.unpack_from('<' + fmt, data, offset)
        offset += struct.calcsize('<' + fmt)
        return result

    def string():
        nonlocal offset
        length, = unpack('H')
        result = data[offset:offset + length].decode()
        offset += length
        return result

    magic, version, count = unpack('III')
    assert magic == 0x4247434e and version == 1
    bones = []
    for _ in range(count):
        bones.append(string())
        unpack('i')
    parts = []
    for _ in range(unpack('I')[0]):
        name = string()
        textured, nv = unpack('BI')
        vertices = np.frombuffer(data, VERTEX, nv, offset).copy()
        offset += nv * VERTEX.itemsize
        nt, = unpack('I')
        triangles = np.frombuffer(data, '<u4', nt * 3, offset).reshape(-1, 3).copy()
        offset += nt * 12
        parts.append((name, vertices, triangles))
    clips = {}
    for _ in range(unpack('I')[0]):
        name = string()
        fps, frames = unpack('fI')
        matrices = np.frombuffer(data, '<f4', frames * count * 12, offset).reshape(frames, count, 3, 4).copy()
        offset += matrices.nbytes
        clips[name] = matrices
    return bones, parts, clips


def boundary_count(vertices, triangles):
    _, inverse = np.unique(np.round(vertices, 5), axis=0, return_inverse=True)
    t = inverse[triangles]
    edges = np.sort(np.concatenate([t[:, [0, 1]], t[:, [1, 2]], t[:, [2, 0]]]), axis=1)
    _, counts = np.unique(edges, axis=0, return_counts=True)
    return int((counts == 1).sum())


if __name__ == '__main__':
    for path in sorted((ROOT / 'src/main/resources/assets/chocobosreborn/entity').glob('*.ncgb')):
        bones, parts, _ = read_mesh(path)
        for name, v, t in parts:
            owner = v['bone'][np.arange(len(v)), np.argmax(v['weight'], axis=1)]
            female = t[~np.any(owner[t] == bones.index('crest_male'), axis=1)]
            print(path.stem, name, 'triangles', len(t), 'boundary edges', boundary_count(v['pos'], t),
                  'old female boundary edges', boundary_count(v['pos'], female),
                  'old female removed triangles', len(t) - len(female))
