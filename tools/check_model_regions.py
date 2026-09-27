"""Check the final region assets independently of the shared Gradle build."""
import numpy as np
from PIL import Image
from model_polish_audit import ROOT, read_mesh, boundary_count

for path in sorted((ROOT / 'src/main/resources/assets/chocobosreborn/entity').glob('*.ncgb')):
    names, before_parts, before_clips = read_mesh(ROOT / 'art/polish/pre-region-repair' / path.name)
    after_names, parts, clips = read_mesh(path)
    _, original, original_tri = before_parts[0]
    _, v, tri = parts[0]
    assert names == after_names
    base_tri = tri[:len(original_tri)]
    expected_added = 12 if path.stem == 'chocobo' else 0
    assert len(tri) == len(original_tri) + expected_added
    if expected_added:
        assert boundary_count(v['pos'], tri[len(original_tri):]) == 0, 'bridge must be a closed volume'
    for field in ('rgb', 'emit'):
        assert np.array_equal(original[field][original_tri], v[field][base_tri]), field
    before_positions = original['pos'][original_tri]
    displacement = v['pos'][base_tri] - before_positions
    assert np.all(displacement[:, :, :2] == 0), 'eye relief only lifts depth'
    assert np.all(displacement[:, :, 2] >= 0) and np.max(displacement[:, :, 2]) < .026
    moved = displacement[:, :, 2] > 0
    assert np.all((before_positions[:, :, 1][moved] > 1.795) & (before_positions[:, :, 1][moved] < 1.925))
    assert np.isfinite(v['normal']).all()
    assert boundary_count(v['pos'], tri) <= boundary_count(original['pos'], original_tri), 'no new holes'
    old_cross = np.cross(before_positions[:, 1] - before_positions[:, 0], before_positions[:, 2] - before_positions[:, 0])
    final_positions = v['pos'][base_tri]
    new_cross = np.cross(final_positions[:, 1] - final_positions[:, 0], final_positions[:, 2] - final_positions[:, 0])
    valid = np.linalg.norm(old_cross, axis=1) > 1e-12
    assert np.all(np.sum(old_cross[valid] * new_cross[valid], axis=1) > 0), 'no inverted faces'
    for name in clips:
        assert np.array_equal(before_clips[name], clips[name]), name
    assert np.isfinite(v['weight']).all() and np.all(v['weight'] >= 0)
    assert np.allclose(v['weight'].sum(axis=1), 1, atol=1e-6)
    assert np.isfinite(v['uv']).all() and np.all((v['uv'] >= 0) & (v['uv'] <= 1))
    changed = np.ones(len(v), bool)
    changed[:len(original)] = np.any(v['uv'][:len(original)] != original['uv'], axis=1)
    folder = ROOT / 'src/main/resources/assets/chocobosreborn/textures/entity' / path.stem
    atlas = np.asarray(Image.open(folder / 'yellow.png').convert('RGB')).astype(int)
    uv = v['uv'][changed]
    col = atlas[(uv[:, 1] * len(atlas)).astype(int), (uv[:, 0] * atlas.shape[1]).astype(int)]
    r, g, b = col.T
    orange = (r > 160) & (g > 60) & (r - g >= 80) & (b < 95)
    feather = (r > 160) & (g > 120) & (r - g < 70) & (b < 120)
    assert np.all(orange | feather), 'replacement UV samples must be orange or feather'
    eyelids = np.asarray(Image.open(folder / 'eyes_blink.png').convert('RGBA'))
    assert eyelids.shape[:2] == atlas.shape[:2]
    assert 100 < np.count_nonzero(eyelids[:, :, 3]) < 15000
    lid_samples = eyelids[(uv[:, 1] * len(eyelids)).astype(int), (uv[:, 0] * eyelids.shape[1]).astype(int), 3]
    assert np.all(lid_samples[orange] == 0), 'eyelids must not recolor the repaired beak'
    for breed in ('purple', 'flame'):
        other = np.asarray(Image.open(folder / f'{breed}.png').convert('RGB')).astype(int)
        sampled = other[(uv[:, 1] * len(other)).astype(int), (uv[:, 0] * other.shape[1]).astype(int)]
        assert np.all(np.max(np.abs(sampled[orange] - col[orange]), axis=1) < 5), 'beak changes with breed'
    edges = np.concatenate([tri[:, [0, 1]], tri[:, [1, 2]], tri[:, [2, 0]]])
    p = v['pos']
    centers = p[edges].mean(axis=1)
    length = np.linalg.norm(p[edges[:, 0]] - p[edges[:, 1]], axis=1)
    region = (centers[:, 2] < -.35) & (centers[:, 1] > 1) & (centers[:, 1] < 1.6) & (np.abs(centers[:, 0]) < .32) & (length > .005)
    edges, length = edges[region], length[region]
    maximum = 0
    for frame in clips['run']:
        m = frame[v['bone']]
        posed = np.sum((np.einsum('vkij,vj->vki', m[:, :, :, :3], p) + m[:, :, :, 3]) * v['weight'][:, :, None], axis=1)
        maximum = max(maximum, float((np.linalg.norm(posed[edges[:, 0]] - posed[edges[:, 1]], axis=1) / length).max()))
    assert maximum < 1.5, maximum
    print(path.stem, 'PASS; tail stretch', round(maximum, 3), 'changed UV corners', int(changed.sum()))
