"""Render breed stills from the shipped atlases without re-running the Meshy import:

    blender -b art/fresh_chocobo.blend -P tools/render_breeds.py -- chocobo purple flame

Writes art/preview/atlas_<variant>_<breed>.png for each breed named (default: all).
"""
import sys
from pathlib import Path

import bpy

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import stills  # noqa: E402
from paint_albedo import PLUMAGE  # noqa: E402

TEXDIR = ROOT / "src/main/resources/assets/chocobosreborn/textures/entity"
args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
variant = args[0] if args else "chocobo"
names = args[1:] or list(PLUMAGE)

body = next(o for o in bpy.data.objects if o.type == "MESH")
cam = bpy.context.scene.camera
if cam is None:
    bpy.ops.object.camera_add()
    cam = bpy.context.object
    bpy.context.scene.camera = cam
nodes = []
for slot in body.material_slots:
    if slot.material and slot.material.node_tree:
        for n in slot.material.node_tree.nodes:
            if n.type == "TEX_IMAGE" and n.image is not None:
                nodes.append(n)
if not nodes:
    raise RuntimeError("no image texture on the body material")
out = ROOT / "art/preview"
out.mkdir(exist_ok=True)
for name in names:
    p = TEXDIR / variant / f"{name}.png"
    # the blend's albedo is packed; swap in a fresh image loaded from the shipped atlas
    img = bpy.data.images.load(str(p), check_existing=False)
    for n in nodes:
        n.image = img
    stills.frame_bird(cam, body)
    stills.render(out / f"atlas_{variant}_{name}.png")
    print("rendered", name)
