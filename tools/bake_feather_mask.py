"""Bake a UV mask of the "show feathers" (tail fan, neck ruff, head crest) for one
shipped variant, from the rig's vertex groups in its saved blend:

    blender -b art/fresh_chocobo.blend -P tools/bake_feather_mask.py -- chocobo

Writes art/masks/<variant>_feathers.png (white = feather). paint_albedo uses it
for the End / Nether birds: those feathers take the end stone / lava texture,
the rest of the plumage stays purple / red (Ahmi: "perfect").
"""
import sys
from pathlib import Path

import bpy
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
variant = args[0] if args else "chocobo"
SIZE = 2048

body = next(o for o in bpy.data.objects if o.type == "MESH")
me = body.data
nv = len(me.vertices)
co = np.empty(nv * 3, dtype=np.float32)
me.vertices.foreach_get("co", co)
co = co.reshape(nv, 3)


def weights(name):
    w = np.zeros(nv, dtype=np.float32)
    vg = body.vertex_groups.get(name)
    if vg is None:
        return w
    idx = vg.index
    for v in me.vertices:
        for g in v.groups:
            if g.group == idx:
                w[v.index] = g.weight
    return w


tail = weights("tail") > 0.5
crest = (weights("crest") + weights("crest_male")) > 0.3
neck = weights("neck") + weights("neck_mid")
# the ruff: neck-band vertices that stand well off the neck's core
neck_v = neck > 0.3
if neck_v.any():
    nx, ny = np.median(co[neck_v, 0]), np.median(co[neck_v, 1])
    r = np.hypot(co[:, 0] - nx, co[:, 1] - ny)
    core = float(np.median(r[neck_v]))
    ruff = neck_v & (r > 1.35 * core)
else:
    ruff = np.zeros(nv, dtype=bool)
flag = (tail | crest | ruff).astype(np.float32)
print("feather verts: tail", int(tail.sum()), "crest", int(crest.sum()), "ruff", int(ruff.sum()), "of", nv)

uv = me.uv_layers.active.data
img = Image.new("L", (SIZE, SIZE), 0)
draw = ImageDraw.Draw(img)
loops = me.loops
painted = 0
for poly in me.polygons:
    vs = [loops[li].vertex_index for li in poly.loop_indices]
    if flag[vs].mean() < 0.5:
        continue
    pts = [(uv[li].uv[0] * SIZE, (1.0 - uv[li].uv[1]) * SIZE) for li in poly.loop_indices]
    draw.polygon(pts, fill=255, outline=255)
    painted += 1
# grow by two texels so the seams do not leak the body colour into the feathers
arr = np.asarray(img).astype(bool)
from scipy import ndimage  # noqa: E402
arr = ndimage.binary_dilation(arr, iterations=2)
out = ROOT / "art/masks"
out.mkdir(exist_ok=True)
Image.fromarray((arr * 255).astype(np.uint8), "L").save(out / f"{variant}_feathers.png")
print("mask", variant, "faces", painted, "texels", int(arr.sum()))
